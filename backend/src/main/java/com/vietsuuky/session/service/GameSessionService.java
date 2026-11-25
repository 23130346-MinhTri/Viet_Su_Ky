package com.vietsuuky.session.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.vietsuuky.quiz.service.QuizService;
import com.vietsuuky.session.entity.IdempotencyRecord;
import com.vietsuuky.session.enums.IdempotencyStatus;
import com.vietsuuky.session.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietsuuky.session.dto.CompleteGameSessionRequest;
import com.vietsuuky.session.dto.GameSessionEventRequest;
import com.vietsuuky.session.dto.GameSessionResponse;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

import java.time.Instant;
import java.time.Clock;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MissionRepository missionRepository;
    private final MissionService missionService;
    private final MissionProgressService missionProgressService;
    private final PlayerRewardService playerRewardService;
    private final PlayerProgressService playerProgressService;
    private final QuizService quizService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GameSessionService(GameSessionRepository gameSessionRepository,
                             PlayerProfileRepository playerProfileRepository,
                             MissionRepository missionRepository,
                             MissionService missionService,
                             MissionProgressService missionProgressService,
                             PlayerRewardService playerRewardService,
                             PlayerProgressService playerProgressService,
                             QuizService quizService,
                             IdempotencyRecordRepository idempotencyRecordRepository,
                             ObjectMapper objectMapper,
                             Clock clock) {
        this.gameSessionRepository = gameSessionRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.missionRepository = missionRepository;
        this.missionService = missionService;
        this.missionProgressService = missionProgressService;
        this.playerRewardService = playerRewardService;
        this.playerProgressService = playerProgressService;
        this.quizService = quizService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
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
        quizService.validateMissionConfiguration(mission);
        if (gameSessionRepository.existsByPlayerIdAndMissionIdAndStatus(
                playerId, missionId, SessionStatus.ACTIVE)) {
            throw new ApiException("SESSION_ALREADY_ACTIVE",
                    "An active session already exists for this mission");
        }
        missionProgressService.markStarted(player, mission);
        GameSession session = new GameSession(player, mission, now);
        session.setMetadata("{\"state\":\"started\"}");
        GameSession saved;
        try {
            saved = gameSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            if (!isActiveSessionConflict(ex)) {
                throw ex;
            }
            throw new ApiException("SESSION_ALREADY_ACTIVE",
                    "An active session already exists for this mission",
                    ex);
        }
        quizService.createSessionSnapshot(saved);
        return saved;
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
        if ("QUIZ".equals(session.getMission().getMissionType().name())) {
            throw new ApiException("QUIZ_ANSWER_ENDPOINT_REQUIRED",
                    "Quiz answers must be submitted through the dedicated quiz answer endpoint");
        }
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
    public GameSessionResponse completeSession(UUID playerId, UUID sessionId, CompleteGameSessionRequest request) {
        return completeSession(playerId, sessionId, request, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public GameSessionResponse completeSession(UUID playerId, UUID sessionId,
                                               CompleteGameSessionRequest request,
                                               String idempotencyKey) {
        Instant now = clock.instant();
        GameSession session = gameSessionRepository.findForUpdateByIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new ApiException("Session not found or access denied"));
        boolean isQuiz = session.getMission().getMissionType() == MissionType.QUIZ;

        if (isQuiz && !quizService.hasExactlyFiveQuestions(sessionId)) {
            throw new ApiException("QUIZ_SESSION_SNAPSHOT_INVALID",
                    "A Quiz session must contain exactly five question snapshots");
        }
        if (isQuiz && (idempotencyKey == null || idempotencyKey.isBlank())) {
            throw new ApiException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key is required to complete a Quiz session");
        }
        if (isQuiz && (request.finalScore() != null || request.durationSeconds() != null)) {
            throw new ApiException("CLIENT_AUTHORITY_FIELDS_FORBIDDEN",
                    "Quiz completion rejects client finalScore and durationSeconds");
        }

        String requestHash = isQuiz
                ? completionRequestHash(sessionId, request)
                : null;
        if (isQuiz) {
            var sessionRecord = idempotencyRecordRepository
                    .findByPlayerIdAndSessionIdAndOperationName(
                            playerId, sessionId, "QUIZ_MISSION_COMPLETION");
            if (sessionRecord.isPresent()
                    && sessionRecord.get().getIdempotencyKey().equals(idempotencyKey)) {
                IdempotencyRecord existing = sessionRecord.get();
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new ApiException("QUIZ_COMPLETION_IDEMPOTENCY_CONFLICT",
                            "The Idempotency-Key was used with a different completion request");
                }
                if (existing.getStatus() == IdempotencyStatus.SUCCESS) {
                    return readCompletionResponse(existing);
                }
                throw new ApiException("QUIZ_COMPLETION_IN_PROGRESS",
                        "The Quiz completion request has not reached a successful terminal state");
            }
            var keyRecord = idempotencyRecordRepository.findByOperationNameAndIdempotencyKey(
                    "QUIZ_MISSION_COMPLETION", idempotencyKey);
            if (keyRecord.isPresent()
                    && !keyRecord.get().getRequestHash().equals(requestHash)) {
                throw new ApiException("QUIZ_COMPLETION_IDEMPOTENCY_CONFLICT",
                        "The Idempotency-Key was used with a different completion request");
            }
        }

        if (expireIfOverdue(session, now)) {
            throw new ApiException("SESSION_EXPIRED", "Session has expired");
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ApiException("SESSION_EXPIRED", "Session has expired");
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException(
                    session.getStatus() == SessionStatus.COMPLETED
                            ? "SESSION_ALREADY_COMPLETED" : "SESSION_NOT_ACTIVE",
                    "Session is already completed or closed");
        }

        int finalScore;
        if (isQuiz) {
            if (!quizService.hasExactlyFiveAnswered(sessionId)) {
                throw new ApiException("QUIZ_COMPLETION_ANSWERS_REQUIRED",
                        "All five quiz answers are required before completion");
            }
            finalScore = quizService.calculateScore(sessionId);
        } else {
            finalScore = request.finalScore() == null ? 0 : Math.max(0, request.finalScore());
        }

        IdempotencyRecord completionRecord = null;
        if (isQuiz) {
            completionRecord = idempotencyRecordRepository
                    .findByPlayerIdAndSessionIdAndOperationName(
                            playerId, sessionId, "QUIZ_MISSION_COMPLETION")
                    .orElseGet(() -> {
                        IdempotencyRecord record = new IdempotencyRecord();
                        record.setPlayer(session.getPlayer());
                        record.setMission(session.getMission());
                        record.setSession(session);
                        record.setOperationName("QUIZ_MISSION_COMPLETION");
                        record.setIdempotencyKey(idempotencyKey);
                        record.setRequestHash(requestHash);
                        record.setStatus(IdempotencyStatus.PENDING);
                        return idempotencyRecordRepository.save(record);
                    });
        }

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
        if (completionRecord != null) {
            GameSessionResponse response = toResponse(savedSession);
            completionRecord.setResponseJson(writeCompletionResponse(response));
            completionRecord.setStatus(IdempotencyStatus.SUCCESS);
            completionRecord.setCompletedAt(clock.instant());
            idempotencyRecordRepository.save(completionRecord);
            return response;
        }
        return toResponse(savedSession);
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

    private String completionRequestHash(UUID sessionId, CompleteGameSessionRequest request) {
        String metadata = request.metadata();
        String canonical = sessionId + "|"
                + (metadata == null ? "null" : metadata.length() + ":" + metadata)
                + "|" + request.finalScore()
                + "|" + request.durationSeconds();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private boolean isActiveSessionConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return "uk_game_sessions_one_active_per_player_mission"
                        .equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String writeCompletionResponse(GameSessionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize Quiz completion response", ex);
        }
    }

    private GameSessionResponse readCompletionResponse(IdempotencyRecord record) {
        if (record.getResponseJson() == null || record.getResponseJson().isBlank()) {
            throw new IllegalStateException("Successful Quiz completion has no stored response");
        }
        try {
            return objectMapper.readValue(record.getResponseJson(), GameSessionResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize stored Quiz completion response", ex);
        }
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
