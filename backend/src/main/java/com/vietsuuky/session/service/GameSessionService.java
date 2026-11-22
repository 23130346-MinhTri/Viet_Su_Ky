package com.vietsuuky.session.service;

import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.mission.service.MissionService;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import com.vietsuuky.progress.entity.MissionProgress;
import com.vietsuuky.progress.enums.MissionProgressStatus;
import com.vietsuuky.progress.service.MissionProgressService;
import com.vietsuuky.progress.service.PlayerProgressService;
import com.vietsuuky.reward.service.PlayerRewardService;
import com.vietsuuky.session.dto.CompleteGameSessionRequest;
import com.vietsuuky.session.dto.GameSessionEventRequest;
import com.vietsuuky.session.dto.GameSessionResponse;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.Clock;
import java.util.UUID;

@Service
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MissionRepository missionRepository;
    private final MissionService missionService;
    private final MissionProgressService missionProgressService;
    private final PlayerRewardService playerRewardService;
    private final PlayerProgressService playerProgressService;
    private final Clock clock;

    public GameSessionService(GameSessionRepository gameSessionRepository,
                             PlayerProfileRepository playerProfileRepository,
                             MissionRepository missionRepository,
                             MissionService missionService,
                             MissionProgressService missionProgressService,
                             PlayerRewardService playerRewardService,
                             PlayerProgressService playerProgressService,
                             Clock clock) {
        this.gameSessionRepository = gameSessionRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.missionRepository = missionRepository;
        this.missionService = missionService;
        this.missionProgressService = missionProgressService;
        this.playerRewardService = playerRewardService;
        this.playerProgressService = playerProgressService;
        this.clock = clock;
    }

    @Transactional
    public GameSession createSession(UUID playerId, UUID missionId) {
        Instant now = clock.instant();
        PlayerProfile player = playerProfileRepository.findById(playerId)
                .orElseThrow(() -> new ApiException("Player not found"));
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException("MISSION_NOT_FOUND", "Mission not found"));
        missionService.ensureMissionAvailable(playerId, mission.getId());
        if (gameSessionRepository.existsByPlayerIdAndMissionIdAndStatus(
                playerId, missionId, SessionStatus.ACTIVE)) {
            throw new ApiException("SESSION_ALREADY_ACTIVE",
                    "An active session already exists for this mission");
        }
        missionProgressService.markStarted(player, mission);
        GameSession session = new GameSession(player, mission, now);
        session.setMetadata("{\"state\":\"started\"}");
        try {
            return gameSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException("SESSION_ALREADY_ACTIVE",
                    "An active session already exists for this mission",
                    ex);
        }
    }

    @Transactional
    public GameSession getSessionForPlayer(UUID playerId, UUID sessionId) {
        GameSession session = gameSessionRepository.findForUpdateByIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new ApiException("Session not found or access denied"));
        expireIfOverdue(session, clock.instant());
        return session;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public GameSession recordEvent(UUID playerId, UUID sessionId, GameSessionEventRequest request) {
        Instant now = clock.instant();
        GameSession session = gameSessionRepository.findForUpdateByIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new ApiException("Session not found or access denied"));
        if (expireIfOverdue(session, now)) {
            throw new ApiException("SESSION_EXPIRED", "Session has expired");
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ApiException("SESSION_EXPIRED", "Session has expired");
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException("Session is no longer active");
        }

        session.setEventCount(session.getEventCount() + 1);
        session.setMetadata(String.format("{\"eventType\":\"%s\", \"payload\":\"%s\"}",
                request.eventType(),
                request.payload() == null ? "" : request.payload()));
        session.setLastActivityAt(now);
        MissionProgress progress = missionProgressService.ensureForPlayer(session.getPlayer(), session.getMission());
        progress.setProgressValue(Math.max(progress.getProgressValue(), 10));
        progress.setLastEventAt(now);
        return gameSessionRepository.save(session);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public GameSession completeSession(UUID playerId, UUID sessionId, CompleteGameSessionRequest request) {
        Instant now = clock.instant();
        GameSession session = gameSessionRepository.findForUpdateByIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new ApiException("Session not found or access denied"));
        if (expireIfOverdue(session, now)) {
            throw new ApiException("SESSION_EXPIRED", "Session has expired");
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ApiException("SESSION_EXPIRED", "Session has expired");
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException("Session is already completed or closed");
        }

        int finalScore = request.finalScore() == null ? 0 : Math.max(0, request.finalScore());
        session.setFinalScore(finalScore);
        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(now);
        if (request.metadata() != null && !request.metadata().isBlank()) {
            session.setMetadata(request.metadata());
        }
        GameSession savedSession = gameSessionRepository.save(session);

        missionProgressService.markCompleted(session.getPlayer(), session.getMission());
        playerRewardService.awardMissionReward(session.getPlayer(), session.getMission());
        playerProgressService.recalculateForPlayer(session.getPlayer());
        return savedSession;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public GameSession abandonSession(UUID playerId, UUID sessionId) {
        Instant now = clock.instant();
        GameSession session = gameSessionRepository.findForUpdateByIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new ApiException("Session not found or access denied"));
        if (expireIfOverdue(session, now)) {
            return session;
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ApiException("SESSION_EXPIRED", "Session has expired");
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException("Session is no longer active");
        }

        session.setStatus(SessionStatus.ABANDONED);
        session.setEndedAt(now);
        return gameSessionRepository.save(session);
    }

    private boolean expireIfOverdue(GameSession session, Instant now) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            return false;
        }

        if (!GameSessionExpirationPolicy.isExpired(
                now, session.getStartedAt(), session.getLastActivityAt())) {
            return false;
        }

        session.setStatus(SessionStatus.EXPIRED);
        session.setEndedAt(now);
        gameSessionRepository.save(session);
        return true;
    }

    public GameSessionResponse toResponse(GameSession session) {
        return new GameSessionResponse(
                session.getId(),
                session.getMission().getId(),
                session.getMission().getCode(),
                session.getMission().getTitle(),
                session.getPlayer().getId(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getLastActivityAt(),
                session.getStatus(),
                session.getEventCount(),
                session.getFinalScore(),
                session.getMetadata()
        );
    }
}
