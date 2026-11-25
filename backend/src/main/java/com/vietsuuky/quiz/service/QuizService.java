package com.vietsuuky.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.quiz.dto.QuizAnswerResult;
import com.vietsuuky.quiz.dto.QuizAnswerRequest;
import com.vietsuuky.quiz.dto.QuizAnswerResponse;
import com.vietsuuky.quiz.dto.QuizOptionResponse;
import com.vietsuuky.quiz.dto.QuizQuestionResponse;
import com.vietsuuky.quiz.dto.QuizQuestionsResponse;
import com.vietsuuky.quiz.entity.QuizAnswerEvidence;
import com.vietsuuky.quiz.entity.QuizAnswerOption;
import com.vietsuuky.quiz.entity.QuizMissionQuestion;
import com.vietsuuky.quiz.entity.QuizQuestion;
import com.vietsuuky.quiz.entity.QuizSessionQuestion;
import com.vietsuuky.quiz.id.QuizQuestionId;
import com.vietsuuky.quiz.id.QuizSessionQuestionId;
import com.vietsuuky.quiz.repository.QuizAnswerEvidenceRepository;
import com.vietsuuky.quiz.repository.QuizAnswerOptionRepository;
import com.vietsuuky.quiz.repository.QuizMissionQuestionRepository;
import com.vietsuuky.quiz.repository.QuizQuestionRepository;
import com.vietsuuky.quiz.repository.QuizSessionQuestionRepository;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import com.vietsuuky.session.service.GameSessionExpirationPolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QuizService {
    private final GameSessionRepository gameSessionRepository;
    private final QuizMissionQuestionRepository missionQuestionRepository;
    private final QuizSessionQuestionRepository sessionQuestionRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAnswerOptionRepository optionRepository;
    private final QuizAnswerEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QuizService(GameSessionRepository gameSessionRepository,
                       QuizMissionQuestionRepository missionQuestionRepository,
                       QuizSessionQuestionRepository sessionQuestionRepository,
                       QuizQuestionRepository questionRepository,
                       QuizAnswerOptionRepository optionRepository,
                       QuizAnswerEvidenceRepository evidenceRepository,
                       ObjectMapper objectMapper,
                       Clock clock) {
        this.gameSessionRepository = gameSessionRepository;
        this.missionQuestionRepository = missionQuestionRepository;
        this.sessionQuestionRepository = sessionQuestionRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.evidenceRepository = evidenceRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void validateMissionConfiguration(Mission mission) {
        if (mission.getMissionType() != MissionType.QUIZ) {
            return;
        }
        validatedMissionQuestions(mission);
    }

    @Transactional
    public void createSessionSnapshot(GameSession session) {
        if (session.getMission().getMissionType() != MissionType.QUIZ) {
            return;
        }
        List<QuizMissionQuestion> missionQuestions = validatedMissionQuestions(session.getMission());
        for (QuizMissionQuestion missionQuestion : missionQuestions) {
            sessionQuestionRepository.save(new QuizSessionQuestion(
                    new QuizSessionQuestionId(
                            session.getId(), missionQuestion.getId().getQuestionOrder()),
                    session.getMission().getId(),
                    missionQuestion.getQuestionId(),
                    missionQuestion.getQuestionVersion()));
        }
    }

    private List<QuizMissionQuestion> validatedMissionQuestions(Mission mission) {
        List<QuizMissionQuestion> missionQuestions =
                missionQuestionRepository.findById_MissionIdOrderById_QuestionOrderAsc(mission.getId());
        if (missionQuestions.size() != 5) {
            throw new ApiException("QUIZ_MISSION_MAPPING_INVALID",
                    "A published Quiz Mission must map exactly five questions");
        }
        for (int index = 0; index < missionQuestions.size(); index++) {
            QuizMissionQuestion missionQuestion = missionQuestions.get(index);
            if (missionQuestion.getId().getQuestionOrder() != index + 1) {
                throw new ApiException("QUIZ_MISSION_MAPPING_INVALID",
                        "A published Quiz Mission must have questions ordered from one to five");
            }
            QuizQuestion question = questionRepository.findById(
                            new QuizQuestionId(
                                    missionQuestion.getQuestionId(),
                                    missionQuestion.getQuestionVersion()))
                    .orElseThrow(() -> new ApiException("QUIZ_QUESTION_NOT_FOUND",
                            "Quiz question not found"));
            if (!question.isActive()) {
                throw new ApiException("QUIZ_QUESTION_NOT_PUBLISHED",
                        "Quiz missions can only use published question versions");
            }
            List<QuizAnswerOption> options =
                    optionRepository.findByQuestionIdAndQuestionVersionOrderByDisplayOrderAsc(
                            missionQuestion.getQuestionId(),
                            missionQuestion.getQuestionVersion());
            if (options.size() < 2 || options.stream().filter(QuizAnswerOption::isCorrect).count() != 1) {
                throw new ApiException("QUIZ_QUESTION_NOT_PUBLISHED",
                        "A published quiz question must have at least two options and exactly one correct option");
            }
        }
        return missionQuestions;
    }

    @Transactional
    public QuizQuestionsResponse getQuestions(UUID playerId, UUID sessionId) {
        GameSession session = gameSessionRepository.findForUpdateByIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new ApiException("SESSION_ACCESS_DENIED",
                        "Session not found or access denied"));
        expireIfOverdue(session);

        List<QuizSessionQuestion> snapshot =
                sessionQuestionRepository.findById_SessionIdOrderById_QuestionOrderAsc(sessionId);
        if (snapshot.size() != 5) {
            throw new ApiException("QUIZ_SESSION_SNAPSHOT_INVALID",
                    "A Quiz session must contain exactly five question snapshots");
        }
        List<QuizAnswerEvidence> evidence =
                evidenceRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId);
        java.util.Map<UUID, QuizAnswerEvidence> evidenceByQuestion = evidence.stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuizAnswerEvidence::getQuestionId,
                        item -> item,
                        (left, right) -> left));

        List<QuizQuestionResponse> responses = snapshot.stream().map(snapshotQuestion -> {
            QuizQuestion question = questionRepository.findById(
                            new QuizQuestionId(
                                    snapshotQuestion.getQuestionId(),
                                    snapshotQuestion.getQuestionVersion()))
                    .orElseThrow(() -> new ApiException("QUIZ_QUESTION_NOT_FOUND",
                            "Quiz question not found"));
            List<QuizOptionResponse> options =
                    optionRepository.findByQuestionIdAndQuestionVersionOrderByDisplayOrderAsc(
                                    snapshotQuestion.getQuestionId(),
                                    snapshotQuestion.getQuestionVersion())
                            .stream()
                            .map(option -> new QuizOptionResponse(
                                    option.getId(),
                                    optionKeyLabel(option.getOptionKey()),
                                    option.getOptionText()))
                            .toList();
            QuizAnswerEvidence recorded = evidenceByQuestion.get(snapshotQuestion.getQuestionId());
            return new QuizQuestionResponse(
                    snapshotQuestion.getQuestionId(),
                    snapshotQuestion.getId().getQuestionOrder(),
                    question.getPrompt(),
                    options,
                    recorded != null,
                    recorded == null ? null : recorded.getSelectedOptionId());
        }).toList();

        return new QuizQuestionsResponse(
                sessionId,
                session.getStatus(),
                evidence.size(),
                responses);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public QuizAnswerResult submitAnswer(UUID playerId, UUID sessionId, QuizAnswerRequest request) {
        GameSession session = gameSessionRepository.findForUpdateByIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new ApiException("SESSION_ACCESS_DENIED",
                        "Session not found or access denied"));

        String requestHash = canonicalRequestHash(sessionId, request);
        Optional<QuizAnswerEvidence> replay =
                evidenceRepository.findBySessionIdAndEventId(sessionId, request.eventId());
        if (replay.isPresent()) {
            QuizAnswerEvidence evidence = replay.get();
            if (!evidence.getRequestHash().equals(requestHash)) {
                throw new ApiException("QUIZ_EVENT_ID_CONFLICT",
                        "The eventId was already used with a different request");
            }
            return new QuizAnswerResult(readStoredResponse(evidence), true);
        }

        if (expireIfOverdue(session)) {
            throw new ApiException("SESSION_EXPIRED", "Session has expired");
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException("SESSION_NOT_ACTIVE", "Session is not active");
        }

        if (sessionQuestionRepository.findById_SessionIdOrderById_QuestionOrderAsc(sessionId).size() != 5) {
            throw new ApiException("QUIZ_SESSION_SNAPSHOT_INVALID",
                    "A Quiz session must contain exactly five question snapshots");
        }

        Optional<QuizSessionQuestion> snapshot =
                sessionQuestionRepository.findById_SessionIdAndQuestionId(
                        sessionId, request.questionId());
        if (snapshot.isEmpty()) {
            throw new ApiException("QUIZ_QUESTION_NOT_IN_SESSION",
                    "Question is not in this session snapshot");
        }
        QuizSessionQuestion snapshotQuestion = snapshot.get();
        if (!snapshotQuestion.getMissionId().equals(session.getMission().getId())) {
            throw new ApiException("QUIZ_QUESTION_NOT_IN_MISSION",
                    "Question belongs to another mission");
        }

        Optional<QuizAnswerEvidence> existing =
                evidenceRepository.findBySessionIdAndQuestionIdAndQuestionVersion(
                        sessionId,
                        snapshotQuestion.getQuestionId(),
                        snapshotQuestion.getQuestionVersion());
        if (existing.isPresent()) {
            throw new ApiException("QUIZ_ANSWER_ALREADY_RECORDED",
                    "A different eventId cannot replace recorded answer evidence");
        }

        List<QuizAnswerEvidence> evidence =
                evidenceRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId);
        int nextOrder = evidence.size() + 1;
        if (snapshotQuestion.getId().getQuestionOrder() != nextOrder) {
            throw new ApiException("QUIZ_QUESTION_ORDER_VIOLATION",
                    "Questions must be answered in fixed order");
        }

        QuizAnswerOption option = optionRepository.findByIdAndQuestionIdAndQuestionVersion(
                        request.selectedOptionId(),
                        snapshotQuestion.getQuestionId(),
                        snapshotQuestion.getQuestionVersion())
                .orElseThrow(() -> new ApiException("QUIZ_OPTION_INVALID",
                        "Selected option is invalid for this question"));

        boolean correct = option.isCorrect();
        int points = correct ? 20 : 0;
        int answeredCount = evidence.size() + 1;
        QuizAnswerResponse response = new QuizAnswerResponse(
                sessionId,
                request.eventId(),
                request.questionId(),
                request.selectedOptionId(),
                answeredCount,
                true);
        QuizAnswerEvidence record = new QuizAnswerEvidence(
                UUID.randomUUID(),
                sessionId,
                session.getMission().getId(),
                snapshotQuestion.getQuestionId(),
                snapshotQuestion.getQuestionVersion(),
                snapshotQuestion.getId().getQuestionOrder(),
                option.getId(),
                request.eventId(),
                requestHash,
                writeStoredResponse(response),
                correct,
                points);
        try {
            evidenceRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException("QUIZ_ANSWER_ALREADY_RECORDED",
                    "The answer evidence could not be recorded because it already exists",
                    ex);
        }
        session.setLastActivityAt(clock.instant());
        gameSessionRepository.save(session);

        return new QuizAnswerResult(response, false);
    }

    public int calculateScore(UUID sessionId) {
        return evidenceRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId)
                .stream()
                .mapToInt(QuizAnswerEvidence::getPoints)
                .sum();
    }

    public boolean hasExactlyFiveAnswered(UUID sessionId) {
        if (evidenceRepository.countBySessionId(sessionId) != 5) {
            return false;
        }
        if (!hasExactlyFiveQuestions(sessionId)) {
            return false;
        }
        return evidenceRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId).size() == 5;
    }

    public boolean hasExactlyFiveQuestions(UUID sessionId) {
        List<QuizSessionQuestion> questions =
                sessionQuestionRepository.findById_SessionIdOrderById_QuestionOrderAsc(sessionId);
        if (questions.size() != 5) {
            return false;
        }
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).getId().getQuestionOrder() != index + 1) {
                return false;
            }
        }
        return true;
    }

    public boolean hasSessionSnapshot(UUID sessionId) {
        return !sessionQuestionRepository.findById_SessionIdOrderById_QuestionOrderAsc(sessionId).isEmpty();
    }

    private boolean expireIfOverdue(GameSession session) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            return false;
        }
        Instant now = clock.instant();
        if (!GameSessionExpirationPolicy.isExpired(
                now, session.getStartedAt(), session.getLastActivityAt())) {
            return false;
        }
        session.setStatus(SessionStatus.EXPIRED);
        session.setEndedAt(now);
        gameSessionRepository.save(session);
        return true;
    }

    private String canonicalRequestHash(UUID sessionId, QuizAnswerRequest request) {
        String canonical = String.join("|",
                request.eventId().toString(),
                sessionId.toString(),
                request.questionId().toString(),
                request.selectedOptionId().toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String optionKeyLabel(String optionKey) {
        if (optionKey == null || optionKey.isBlank()) {
            throw new ApiException("QUIZ_OPTION_INVALID", "Quiz option key is required");
        }
        return "Option " + optionKey;
    }

    private String writeStoredResponse(QuizAnswerResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize quiz answer response", ex);
        }
    }

    private QuizAnswerResponse readStoredResponse(QuizAnswerEvidence evidence) {
        try {
            return objectMapper.readValue(evidence.getResponseJson(), QuizAnswerResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize stored quiz answer response", ex);
        }
    }
}
