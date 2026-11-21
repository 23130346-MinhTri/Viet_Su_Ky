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

import java.time.Instant;
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

    public GameSessionService(GameSessionRepository gameSessionRepository,
                             PlayerProfileRepository playerProfileRepository,
                             MissionRepository missionRepository,
                             MissionService missionService,
                             MissionProgressService missionProgressService,
                             PlayerRewardService playerRewardService,
                             PlayerProgressService playerProgressService) {
        this.gameSessionRepository = gameSessionRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.missionRepository = missionRepository;
        this.missionService = missionService;
        this.missionProgressService = missionProgressService;
        this.playerRewardService = playerRewardService;
        this.playerProgressService = playerProgressService;
    }

    @Transactional
    public GameSession createSession(UUID playerId, UUID missionId) {
        PlayerProfile player = playerProfileRepository.findById(playerId)
                .orElseThrow(() -> new ApiException("Player not found"));
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException("Mission not found"));
        missionService.ensureMissionAvailable(playerId, mission.getId());
        missionProgressService.markStarted(player, mission);
        GameSession session = new GameSession(player, mission);
        session.setMetadata("{\"state\":\"started\"}");
        return gameSessionRepository.save(session);
    }

    public GameSession getSessionForPlayer(UUID playerId, UUID sessionId) {
        return gameSessionRepository.findByIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new ApiException("Session not found or access denied"));
    }

    @Transactional
    public GameSession recordEvent(UUID playerId, UUID sessionId, GameSessionEventRequest request) {
        GameSession session = getSessionForPlayer(playerId, sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException("Session is no longer active");
        }

        session.setEventCount(session.getEventCount() + 1);
        session.setMetadata(String.format("{\"eventType\":\"%s\", \"payload\":\"%s\"}",
                request.eventType(),
                request.payload() == null ? "" : request.payload()));
        MissionProgress progress = missionProgressService.ensureForPlayer(session.getPlayer(), session.getMission());
        progress.setProgressValue(Math.max(progress.getProgressValue(), 10));
        progress.setLastEventAt(Instant.now());
        return gameSessionRepository.save(session);
    }

    @Transactional
    public GameSession completeSession(UUID playerId, UUID sessionId, CompleteGameSessionRequest request) {
        GameSession session = getSessionForPlayer(playerId, sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException("Session is already completed or closed");
        }

        int finalScore = request.finalScore() == null ? 0 : Math.max(0, request.finalScore());
        session.setFinalScore(finalScore);
        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(Instant.now());
        if (request.metadata() != null && !request.metadata().isBlank()) {
            session.setMetadata(request.metadata());
        }
        GameSession savedSession = gameSessionRepository.save(session);

        missionProgressService.markCompleted(session.getPlayer(), session.getMission());
        playerRewardService.awardMissionReward(session.getPlayer(), session.getMission());
        playerProgressService.recalculateForPlayer(session.getPlayer());
        return savedSession;
    }

    @Transactional
    public GameSession abandonSession(UUID playerId, UUID sessionId) {
        GameSession session = getSessionForPlayer(playerId, sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException("Session is no longer active");
        }

        session.setStatus(SessionStatus.ABANDONED);
        session.setEndedAt(Instant.now());
        return gameSessionRepository.save(session);
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
                session.getStatus(),
                session.getEventCount(),
                session.getFinalScore(),
                session.getMetadata()
        );
    }
}
