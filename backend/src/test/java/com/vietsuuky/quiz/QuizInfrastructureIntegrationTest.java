package com.vietsuuky.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.service.AccountService;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.progress.enums.MissionProgressStatus;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import com.vietsuuky.progress.service.PlayerProgressService;
import com.vietsuuky.quiz.dto.QuizAnswerRequest;
import com.vietsuuky.quiz.dto.QuizAnswerResult;
import com.vietsuuky.quiz.dto.QuizQuestionsResponse;
import com.vietsuuky.quiz.entity.QuizAnswerOption;
import com.vietsuuky.quiz.entity.QuizMissionQuestion;
import com.vietsuuky.quiz.entity.QuizQuestion;
import com.vietsuuky.quiz.id.QuizMissionQuestionId;
import com.vietsuuky.quiz.id.QuizQuestionId;
import com.vietsuuky.quiz.repository.QuizAnswerEvidenceRepository;
import com.vietsuuky.quiz.repository.QuizAnswerOptionRepository;
import com.vietsuuky.quiz.repository.QuizMissionQuestionRepository;
import com.vietsuuky.quiz.repository.QuizQuestionRepository;
import com.vietsuuky.quiz.service.QuizService;
import com.vietsuuky.reward.enums.RewardType;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import com.vietsuuky.session.dto.CompleteGameSessionRequest;
import com.vietsuuky.session.dto.GameSessionEventRequest;
import com.vietsuuky.session.dto.GameSessionResponse;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import com.vietsuuky.session.service.GameSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class QuizInfrastructureIntegrationTest {
    @Autowired
    private AccountService accountService;
    @Autowired
    private PlayerProfileService playerProfileService;
    @Autowired
    private MissionRepository missionRepository;
    @Autowired
    private QuizQuestionRepository questionRepository;
    @Autowired
    private QuizAnswerOptionRepository optionRepository;
    @Autowired
    private QuizMissionQuestionRepository missionQuestionRepository;
    @Autowired
    private QuizAnswerEvidenceRepository evidenceRepository;
    @Autowired
    private GameSessionService gameSessionService;
    @Autowired
    private GameSessionRepository gameSessionRepository;
    @Autowired
    private QuizService quizService;
    @Autowired
    private PlayerRewardRepository rewardRepository;
    @Autowired
    private MissionProgressRepository missionProgressRepository;
    @Autowired
    private PlayerProgressService playerProgressService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deliversFiveOrderedQuestionsAndPersistsCorrectAndIncorrectScoring() throws Exception {
        Fixture fixture = createFixture(5);
        GameSession session = startSession(fixture);

        QuizQuestionsResponse delivery = quizService.getQuestions(
                fixture.player().getId(), session.getId());

        assertEquals(5, delivery.questions().size());
        assertEquals(0, delivery.answeredCount());
        for (int index = 0; index < 5; index++) {
            assertEquals(fixture.questionIds()[index], delivery.questions().get(index).questionId());
            assertEquals(index + 1, delivery.questions().get(index).order());
        }
        assertEquals("Correct test answer 1", delivery.questions().get(0).options().get(1).text());
        assertFalse(objectMapper.writeValueAsString(delivery).contains("isCorrect"));

        answer(fixture, session, 0, true, UUID.randomUUID());
        answer(fixture, session, 1, false, UUID.randomUUID());

        var evidence = evidenceRepository.findBySessionIdOrderByQuestionOrderAsc(session.getId());
        assertEquals(2, evidence.size());
        assertTrue(evidence.get(0).isCorrect());
        assertEquals(20, evidence.get(0).getPoints());
        assertFalse(evidence.get(1).isCorrect());
        assertEquals(0, evidence.get(1).getPoints());
        assertEquals(20, quizService.calculateScore(session.getId()));
    }

    @Test
    void rejectsSkippedAnswersAndAnyAnswerAfterTheFiveQuestionSnapshot() {
        Fixture fixture = createFixture(5);
        GameSession session = startSession(fixture);

        ApiException skipped = assertThrows(ApiException.class,
                () -> answer(fixture, session, 2, true, UUID.randomUUID()));
        assertEquals("QUIZ_QUESTION_ORDER_VIOLATION", skipped.getErrorCode());

        for (int index = 0; index < 5; index++) {
            answer(fixture, session, index, true, UUID.randomUUID());
        }
        ApiException sixth = assertThrows(ApiException.class,
                () -> quizService.submitAnswer(
                        fixture.player().getId(),
                        session.getId(),
                        new QuizAnswerRequest(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));
        assertEquals("QUIZ_QUESTION_NOT_IN_SESSION", sixth.getErrorCode());
        assertEquals(5, evidenceRepository.countBySessionId(session.getId()));
    }

    @Test
    void replaysTheOriginalAnswerResponseAndRejectsEventAndQuestionConflicts() {
        Fixture fixture = createFixture(5);
        GameSession session = startSession(fixture);
        UUID eventId = UUID.randomUUID();
        QuizAnswerRequest firstRequest =
                new QuizAnswerRequest(eventId, fixture.questionIds()[0], fixture.correctOptionIds()[0]);
        QuizAnswerResult first = quizService.submitAnswer(
                fixture.player().getId(), session.getId(), firstRequest);
        answer(fixture, session, 1, false, UUID.randomUUID());

        QuizAnswerResult replay = quizService.submitAnswer(
                fixture.player().getId(), session.getId(), firstRequest);
        assertTrue(replay.replayed());
        assertEquals(first.response(), replay.response());
        assertEquals(2, evidenceRepository.countBySessionId(session.getId()));

        ApiException eventConflict = assertThrows(ApiException.class,
                () -> quizService.submitAnswer(
                        fixture.player().getId(),
                        session.getId(),
                        new QuizAnswerRequest(
                                eventId, fixture.questionIds()[0], fixture.incorrectOptionIds()[0])));
        assertEquals("QUIZ_EVENT_ID_CONFLICT", eventConflict.getErrorCode());

        ApiException questionConflict = assertThrows(ApiException.class,
                () -> quizService.submitAnswer(
                        fixture.player().getId(),
                        session.getId(),
                        new QuizAnswerRequest(
                                UUID.randomUUID(),
                                fixture.questionIds()[0],
                                fixture.incorrectOptionIds()[0])));
        assertEquals("QUIZ_ANSWER_ALREADY_RECORDED", questionConflict.getErrorCode());
    }

    @Test
    void quizGenericEventsAreRejectedWithoutMutatingSessionOrProgress() {
        Fixture fixture = createFixture(5);
        GameSession session = startSession(fixture);
        Instant lastActivity = session.getLastActivityAt();
        var progress = missionProgressRepository
                .findByPlayerIdAndMissionId(fixture.player().getId(), fixture.mission().getId())
                .orElseThrow();
        Instant lastEventAt = progress.getLastEventAt();

        ApiException rejected = assertThrows(ApiException.class,
                () -> gameSessionService.recordEvent(
                        fixture.player().getId(),
                        session.getId(),
                        new GameSessionEventRequest("CHECKPOINT_REACHED", "{\"score\":999}")));
        assertEquals("QUIZ_ANSWER_ENDPOINT_REQUIRED", rejected.getErrorCode());

        GameSession unchanged = gameSessionRepository.findById(session.getId()).orElseThrow();
        var unchangedProgress = missionProgressRepository
                .findByPlayerIdAndMissionId(fixture.player().getId(), fixture.mission().getId())
                .orElseThrow();
        assertEquals(0, unchanged.getEventCount());
        assertTrue(Duration.between(lastActivity, unchanged.getLastActivityAt()).abs().toNanos() < 1_000_000);
        assertEquals(0, unchangedProgress.getProgressValue());
        assertTrue(Duration.between(lastEventAt, unchangedProgress.getLastEventAt()).abs().toNanos() < 1_000_000);
        assertEquals(0, evidenceRepository.countBySessionId(session.getId()));
    }

    @Test
    void completionRequiresAllAnswersUsesServerScoreAndIsIdempotent() {
        Fixture fixture = createFixture(5);
        GameSession session = startSession(fixture);
        String idempotencyKey = UUID.randomUUID().toString();
        CompleteGameSessionRequest request =
                new CompleteGameSessionRequest(null, "{\"source\":\"test\"}", null);

        ApiException incomplete = assertThrows(ApiException.class,
                () -> gameSessionService.completeSession(
                        fixture.player().getId(), session.getId(), request, idempotencyKey));
        assertEquals("QUIZ_COMPLETION_ANSWERS_REQUIRED", incomplete.getErrorCode());
        assertEquals(SessionStatus.ACTIVE,
                gameSessionRepository.findById(session.getId()).orElseThrow().getStatus());
        assertFalse(rewardRepository.existsByPlayerIdAndMissionIdAndRewardType(
                fixture.player().getId(), fixture.mission().getId(), RewardType.MISSION_COMPLETE));

        for (int index = 0; index < 5; index++) {
            answer(fixture, session, index, index != 2, UUID.randomUUID());
        }
        ApiException forgedScore = assertThrows(ApiException.class,
                () -> gameSessionService.completeSession(
                        fixture.player().getId(),
                        session.getId(),
                        new CompleteGameSessionRequest(999, request.metadata(), null),
                        idempotencyKey));
        assertEquals("CLIENT_AUTHORITY_FIELDS_FORBIDDEN", forgedScore.getErrorCode());
        assertEquals(SessionStatus.ACTIVE,
                gameSessionRepository.findById(session.getId()).orElseThrow().getStatus());
        assertTrue(rewardRepository.findByPlayerIdOrderByClaimedAtDesc(
                fixture.player().getId()).isEmpty());

        GameSessionResponse completed = gameSessionService.completeSession(
                fixture.player().getId(), session.getId(), request, idempotencyKey);
        assertEquals(80, completed.finalScore());
        assertEquals(SessionStatus.COMPLETED, completed.status());

        GameSessionResponse replay = gameSessionService.completeSession(
                fixture.player().getId(), session.getId(), request, idempotencyKey);
        assertEquals(completed, replay);

        ApiException changedPayload = assertThrows(ApiException.class,
                () -> gameSessionService.completeSession(
                        fixture.player().getId(),
                        session.getId(),
                        new CompleteGameSessionRequest(null, "{\"source\":\"changed\"}", null),
                        idempotencyKey));
        assertEquals("QUIZ_COMPLETION_IDEMPOTENCY_CONFLICT", changedPayload.getErrorCode());

        ApiException differentKey = assertThrows(ApiException.class,
                () -> gameSessionService.completeSession(
                        fixture.player().getId(),
                        session.getId(),
                        request,
                        UUID.randomUUID().toString()));
        assertEquals("SESSION_ALREADY_COMPLETED", differentKey.getErrorCode());

        var missionProgress = missionProgressRepository
                .findByPlayerIdAndMissionId(fixture.player().getId(), fixture.mission().getId())
                .orElseThrow();
        var playerProgress = playerProgressService.getPlayerProgress(fixture.player().getId());
        assertEquals(MissionProgressStatus.COMPLETED, missionProgress.getStatus());
        assertEquals(100, missionProgress.getProgressValue());
        assertEquals(1, playerProgress.totalMissionsCompleted());
        assertEquals(fixture.mission().getRewardPoints(), playerProgress.totalPoints());
        assertEquals(1, rewardRepository.findByPlayerIdOrderByClaimedAtDesc(
                fixture.player().getId()).size());
        assertEquals(5, evidenceRepository.countBySessionId(session.getId()));
    }

    @Test
    void quizWithoutExactlyFivePublishedQuestionsCannotStart() {
        Fixture fixture = createFixture(4);

        ApiException invalidMission = assertThrows(ApiException.class,
                () -> gameSessionService.createSession(
                        fixture.player().getId(), fixture.mission().getId()));

        assertEquals("QUIZ_MISSION_MAPPING_INVALID", invalidMission.getErrorCode());
        assertTrue(missionProgressRepository
                .findByPlayerIdAndMissionId(fixture.player().getId(), fixture.mission().getId())
                .isEmpty());
    }

    @Test
    void quizSessionAccessIsBoundToTheAuthenticatedPlayer() {
        Fixture fixture = createFixture(5);
        GameSession session = startSession(fixture);
        Account otherAccount = accountService.createAccount(
                "quiz-other-" + UUID.randomUUID() + "@test.com", "Password123!");
        PlayerProfile otherPlayer = playerProfileService.createForAccount(
                otherAccount, "Quiz Other Player");

        assertThrows(ApiException.class,
                () -> quizService.getQuestions(otherPlayer.getId(), session.getId()));
        assertThrows(ApiException.class,
                () -> answer(fixture, session, 0, true, UUID.randomUUID(), otherPlayer.getId()));
    }

    private Fixture createFixture(int mappedQuestionCount) {
        String token = UUID.randomUUID().toString();
        Account account = accountService.createAccount(
                "quiz-" + token + "@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Quiz " + token);
        Mission mission = missionRepository.saveAndFlush(new Mission(
                "QUIZ-" + token,
                "Test Quiz",
                "CHAPTER_1",
                1,
                MissionType.QUIZ,
                40));

        UUID[] questionIds = new UUID[5];
        UUID[] correctOptionIds = new UUID[5];
        UUID[] incorrectOptionIds = new UUID[5];
        for (int index = 0; index < 5; index++) {
            int order = index + 1;
            UUID questionId = UUID.randomUUID();
            questionIds[index] = questionId;
            QuizQuestion question = questionRepository.saveAndFlush(new QuizQuestion(
                    new QuizQuestionId(questionId, 1),
                    "Test question " + order,
                    "Test explanation",
                    false));
            UUID incorrectOptionId = UUID.randomUUID();
            UUID correctOptionId = UUID.randomUUID();
            incorrectOptionIds[index] = incorrectOptionId;
            correctOptionIds[index] = correctOptionId;
            optionRepository.saveAndFlush(new QuizAnswerOption(
                    incorrectOptionId, questionId, 1, "A", "Incorrect test answer " + order, 1, false));
            optionRepository.saveAndFlush(new QuizAnswerOption(
                    correctOptionId, questionId, 1, "B", "Correct test answer " + order, 2, true));
            question.activate();
            questionRepository.saveAndFlush(question);
            if (order <= mappedQuestionCount) {
                missionQuestionRepository.saveAndFlush(new QuizMissionQuestion(
                        new QuizMissionQuestionId(mission.getId(), order),
                        questionId,
                        1));
            }
        }
        return new Fixture(player, mission, questionIds, correctOptionIds, incorrectOptionIds);
    }

    private GameSession startSession(Fixture fixture) {
        return gameSessionService.createSession(fixture.player().getId(), fixture.mission().getId());
    }

    private QuizAnswerResult answer(Fixture fixture,
                                    GameSession session,
                                    int questionIndex,
                                    boolean correct,
                                    UUID eventId) {
        return answer(fixture, session, questionIndex, correct, eventId, fixture.player().getId());
    }

    private QuizAnswerResult answer(Fixture fixture,
                                    GameSession session,
                                    int questionIndex,
                                    boolean correct,
                                    UUID eventId,
                                    UUID playerId) {
        UUID optionId = correct
                ? fixture.correctOptionIds()[questionIndex]
                : fixture.incorrectOptionIds()[questionIndex];
        return quizService.submitAnswer(
                playerId,
                session.getId(),
                new QuizAnswerRequest(eventId, fixture.questionIds()[questionIndex], optionId));
    }

    private record Fixture(PlayerProfile player,
                           Mission mission,
                           UUID[] questionIds,
                           UUID[] correctOptionIds,
                           UUID[] incorrectOptionIds) {
    }
}
