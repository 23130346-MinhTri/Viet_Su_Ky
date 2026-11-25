package com.vietsuuky.quiz;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.service.AccountService;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import com.vietsuuky.progress.service.PlayerProgressService;
import com.vietsuuky.quiz.dto.QuizAnswerRequest;
import com.vietsuuky.quiz.dto.QuizAnswerResult;
import com.vietsuuky.quiz.entity.QuizAnswerEvidence;
import com.vietsuuky.quiz.entity.QuizAnswerOption;
import com.vietsuuky.quiz.entity.QuizMissionQuestion;
import com.vietsuuky.quiz.entity.QuizQuestion;
import com.vietsuuky.quiz.id.QuizMissionQuestionId;
import com.vietsuuky.quiz.id.QuizQuestionId;
import com.vietsuuky.quiz.repository.QuizAnswerEvidenceRepository;
import com.vietsuuky.quiz.repository.QuizAnswerOptionRepository;
import com.vietsuuky.quiz.repository.QuizMissionQuestionRepository;
import com.vietsuuky.quiz.repository.QuizQuestionRepository;
import com.vietsuuky.quiz.repository.QuizSessionQuestionRepository;
import com.vietsuuky.quiz.service.QuizService;
import com.vietsuuky.reward.enums.RewardType;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import com.vietsuuky.session.dto.CompleteGameSessionRequest;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import com.vietsuuky.session.service.GameSessionService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "QUIZ_POSTGRES_INTEGRATION_TESTS", matches = "true")
class QuizPostgresVerificationTest {
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> requiredEnvironment("QUIZ_POSTGRES_INTEGRATION_URL"));
        registry.add("spring.datasource.username",
                () -> requiredEnvironment("QUIZ_POSTGRES_INTEGRATION_USER"));
        registry.add("spring.datasource.password",
                () -> requiredEnvironment("QUIZ_POSTGRES_INTEGRATION_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "app");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.schemas", () -> "app");
        registry.add("spring.flyway.default-schema", () -> "app");
        registry.add("app.jwt.secret",
                () -> "01234567890123456789012345678901234567890123456789");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for Quiz PostgreSQL tests");
        }
        return value;
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Flyway flyway;
    @Autowired
    private TestRestTemplate restTemplate;
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
    private QuizSessionQuestionRepository sessionQuestionRepository;
    @Autowired
    private QuizAnswerEvidenceRepository evidenceRepository;
    @Autowired
    private GameSessionRepository gameSessionRepository;
    @Autowired
    private GameSessionService gameSessionService;
    @Autowired
    private QuizService quizService;
    @Autowired
    private MissionProgressRepository missionProgressRepository;
    @Autowired
    private PlayerRewardRepository rewardRepository;
    @Autowired
    private PlayerProgressService playerProgressService;

    @Test
    void cleanPostgres16MigrationValidatesHibernateAndHealth() {
        assertEquals("16", jdbcTemplate.queryForObject(
                "SELECT split_part(current_setting('server_version'), '.', 1)", String.class));
        assertEquals("10", flyway.info().current().getVersion().getVersion());
        assertEquals(10, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app.flyway_schema_history WHERE version IS NOT NULL AND success = TRUE",
                Integer.class));
        assertEquals(200, restTemplate.getForEntity("/actuator/health", String.class).getStatusCode().value());
    }

    @Test
    void migrationCreatesCompositeForeignKeysUniqueEvidenceIndexesAndCorrectOptionIndex() {
        assertEquals(4, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname IN (
                    'fk_quiz_session_questions_session',
                    'fk_quiz_session_questions_mission_question',
                    'fk_quiz_answer_evidence_snapshot',
                    'fk_quiz_answer_evidence_option')
                """, Integer.class));
        assertEquals(7, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname IN (
                    'uk_quiz_answer_evidence_question',
                    'uk_quiz_answer_evidence_order',
                    'uk_quiz_answer_evidence_event',
                    'uk_quiz_answer_evidence_exact',
                    'uk_quiz_session_questions_question',
                    'uk_quiz_session_questions_exact',
                    'uk_quiz_answer_options_exact')
                """, Integer.class));
        String correctIndex = jdbcTemplate.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'app'
                  AND indexname = 'uk_quiz_answer_options_single_correct'
                """, String.class);
        assertTrue(correctIndex.contains("WHERE (is_correct = true)"));
    }

    @Test
    void postgresEnforcesPublishedContentSnapshotAndEvidenceImmutability() {
        Fixture fixture = createFixture(4, null);
        assertThrows(ApiException.class,
                () -> gameSessionService.createSession(fixture.player().getId(), fixture.mission().getId()));
        assertThrows(DataAccessException.class,
                () -> gameSessionRepository.saveAndFlush(new GameSession(fixture.player(), fixture.mission())));

        addFifthMissionQuestion(fixture);
        GameSession session = gameSessionService.createSession(
                fixture.player().getId(), fixture.mission().getId());
        assertEquals(5, sessionQuestionRepository
                .findById_SessionIdOrderById_QuestionOrderAsc(session.getId()).size());

        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        "UPDATE app.quiz_questions SET prompt = 'changed' WHERE id = ? AND version = 1",
                        fixture.questionIds()[0]));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        "UPDATE app.quiz_answer_options SET is_correct = FALSE WHERE id = ?",
                        fixture.correctOptionIds()[0]));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        "UPDATE app.quiz_answer_options SET question_id = ?, question_version = 1 WHERE id = ?",
                        fixture.questionIds()[1],
                        fixture.correctOptionIds()[0]));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        "DELETE FROM app.quiz_session_questions WHERE session_id = ? AND question_order = 1",
                        session.getId()));

        answer(fixture, session, 0, true, UUID.randomUUID());
        QuizAnswerEvidence persisted = evidenceRepository
                .findBySessionIdOrderByQuestionOrderAsc(session.getId()).get(0);
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        "UPDATE app.quiz_answer_evidence SET points = 0 WHERE id = ?",
                        persisted.getId()));

        QuizAnswerEvidence forged = new QuizAnswerEvidence(
                UUID.randomUUID(),
                session.getId(),
                fixture.mission().getId(),
                fixture.questionIds()[1],
                1,
                2,
                fixture.correctOptionIds()[1],
                UUID.randomUUID(),
                "forged",
                "{}",
                false,
                0);
        assertThrows(DataAccessException.class, () -> evidenceRepository.saveAndFlush(forged));
    }

    @Test
    void concurrentAnswerSubmissionsForOneQuestionPersistExactlyOneEvidenceRow() throws Exception {
        Fixture fixture = createFixture(5, null);
        GameSession session = gameSessionService.createSession(
                fixture.player().getId(), fixture.mission().getId());
        List<Object> outcomes = runConcurrently(
                () -> submit(fixture, session, 0, true, UUID.randomUUID()),
                () -> submit(fixture, session, 0, false, UUID.randomUUID()));

        assertEquals(1, outcomes.stream().filter(QuizAnswerResult.class::isInstance).count());
        List<ApiException> failures = outcomes.stream()
                .filter(ApiException.class::isInstance)
                .map(ApiException.class::cast)
                .toList();
        assertEquals(1, failures.size());
        assertEquals("QUIZ_ANSWER_ALREADY_RECORDED", failures.get(0).getErrorCode());
        assertEquals(1, evidenceRepository.countBySessionId(session.getId()));
    }

    @Test
    void expiredCompletionRacingAnAnswerRemainsExpiredWithoutEvidenceOrReward() throws Exception {
        Fixture fixture = createFixture(5, null);
        GameSession session = gameSessionService.createSession(
                fixture.player().getId(), fixture.mission().getId());
        session.setLastActivityAt(Instant.now().minus(31, ChronoUnit.MINUTES));
        gameSessionRepository.saveAndFlush(session);

        List<Object> outcomes = runConcurrently(
                () -> gameSessionService.completeSession(
                        fixture.player().getId(),
                        session.getId(),
                        new CompleteGameSessionRequest(null, "{}", null),
                        UUID.randomUUID().toString()),
                () -> submit(fixture, session, 0, true, UUID.randomUUID()));

        assertEquals(2, outcomes.stream().filter(ApiException.class::isInstance).count());
        GameSession persisted = gameSessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, persisted.getStatus());
        assertNotNull(persisted.getEndedAt());
        assertEquals(0, persisted.getFinalScore());
        assertEquals(0, evidenceRepository.countBySessionId(session.getId()));
        assertTrue(rewardRepository.findByPlayerIdOrderByClaimedAtDesc(
                fixture.player().getId()).isEmpty());
    }

    @Test
    void concurrentQuizCompletionsKeepPlayerAndMissionProgressConsistent() throws Exception {
        Fixture first = createFixture(5, null);
        Fixture second = createFixture(5, first.player());
        GameSession firstSession = gameSessionService.createSession(
                first.player().getId(), first.mission().getId());
        GameSession secondSession = gameSessionService.createSession(
                second.player().getId(), second.mission().getId());
        answerAll(first, firstSession);
        answerAll(second, secondSession);

        List<Object> outcomes = runConcurrently(
                () -> gameSessionService.completeSession(
                        first.player().getId(),
                        firstSession.getId(),
                        new CompleteGameSessionRequest(null, "first", null),
                        UUID.randomUUID().toString()),
                () -> gameSessionService.completeSession(
                        second.player().getId(),
                        secondSession.getId(),
                        new CompleteGameSessionRequest(null, "second", null),
                        UUID.randomUUID().toString()));

        assertEquals(2, outcomes.stream().filter(outcome -> !(outcome instanceof ApiException)).count());
        assertEquals(2, missionProgressRepository.findByPlayerIdOrderByMission_MissionOrderAsc(
                first.player().getId()).stream()
                .filter(progress -> progress.getStatus().name().equals("COMPLETED"))
                .count());
        var progress = playerProgressService.getPlayerProgress(first.player().getId());
        assertEquals(2, progress.totalMissionsCompleted());
        assertEquals(first.mission().getRewardPoints() + second.mission().getRewardPoints(),
                progress.totalPoints());
        assertEquals(2, rewardRepository.findByPlayerIdOrderByClaimedAtDesc(
                first.player().getId()).size());
    }

    @Test
    void publishedQuestionRequiresExactlyOneCorrectOption() {
        QuizQuestion question = questionRepository.saveAndFlush(new QuizQuestion(
                new QuizQuestionId(UUID.randomUUID(), 1),
                "Unpublished test question",
                "Test explanation",
                false));
        optionRepository.saveAndFlush(new QuizAnswerOption(
                UUID.randomUUID(),
                question.getId().getId(),
                question.getId().getVersion(),
                "A",
                "Incorrect option A",
                1,
                false));
        optionRepository.saveAndFlush(new QuizAnswerOption(
                UUID.randomUUID(),
                question.getId().getId(),
                question.getId().getVersion(),
                "B",
                "Incorrect option B",
                2,
                false));
        question.activate();

        assertThrows(DataAccessException.class, () -> questionRepository.saveAndFlush(question));
    }

    private Fixture createFixture(int mappedCount, PlayerProfile existingPlayer) {
        String unique = UUID.randomUUID().toString();
        PlayerProfile player = existingPlayer;
        if (player == null) {
            Account account = accountService.createAccount(
                    "quiz-pg-" + unique + "@test.com", "Password123!");
            player = playerProfileService.createForAccount(account, "Quiz PG " + unique);
        }
        Mission mission = missionRepository.saveAndFlush(new Mission(
                "QUIZ-PG-" + unique,
                "PostgreSQL test mission",
                "CHAPTER_1",
                1,
                MissionType.QUIZ,
                25));
        UUID[] questionIds = new UUID[5];
        UUID[] correctOptionIds = new UUID[5];
        UUID[] incorrectOptionIds = new UUID[5];
        Fixture fixture = new Fixture(player, mission, questionIds, correctOptionIds, incorrectOptionIds);
        for (int index = 0; index < 5; index++) {
            int order = index + 1;
            UUID questionId = UUID.randomUUID();
            questionIds[index] = questionId;
            QuizQuestion question = questionRepository.saveAndFlush(new QuizQuestion(
                    new QuizQuestionId(questionId, 1),
                    "PostgreSQL test question " + order,
                    "Test explanation",
                    false));
            UUID incorrectOptionId = UUID.randomUUID();
            UUID correctOptionId = UUID.randomUUID();
            incorrectOptionIds[index] = incorrectOptionId;
            correctOptionIds[index] = correctOptionId;
            optionRepository.saveAndFlush(new QuizAnswerOption(
                    incorrectOptionId, questionId, 1, "A", "Incorrect " + order, 1, false));
            optionRepository.saveAndFlush(new QuizAnswerOption(
                    correctOptionId, questionId, 1, "B", "Correct " + order, 2, true));
            question.activate();
            questionRepository.saveAndFlush(question);
            if (order <= mappedCount) {
                missionQuestionRepository.saveAndFlush(new QuizMissionQuestion(
                        new QuizMissionQuestionId(mission.getId(), order),
                        questionId,
                        1));
            }
        }
        return fixture;
    }

    private void addFifthMissionQuestion(Fixture fixture) {
        missionQuestionRepository.saveAndFlush(new QuizMissionQuestion(
                new QuizMissionQuestionId(fixture.mission().getId(), 5),
                fixture.questionIds()[4],
                1));
    }

    private QuizAnswerResult submit(Fixture fixture,
                                    GameSession session,
                                    int questionIndex,
                                    boolean correct,
                                    UUID eventId) {
        UUID optionId = correct
                ? fixture.correctOptionIds()[questionIndex]
                : fixture.incorrectOptionIds()[questionIndex];
        return quizService.submitAnswer(
                fixture.player().getId(),
                session.getId(),
                new QuizAnswerRequest(eventId, fixture.questionIds()[questionIndex], optionId));
    }

    private void answer(Fixture fixture,
                        GameSession session,
                        int questionIndex,
                        boolean correct,
                        UUID eventId) {
        submit(fixture, session, questionIndex, correct, eventId);
    }

    private void answerAll(Fixture fixture, GameSession session) {
        for (int index = 0; index < 5; index++) {
            answer(fixture, session, index, true, UUID.randomUUID());
        }
    }

    private List<Object> runConcurrently(Supplier<?> first, Supplier<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> one = executor.submit(() -> execute(first, ready, start));
            Future<Object> two = executor.submit(() -> execute(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Object execute(Supplier<?> operation, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start");
            }
            return operation.get();
        } catch (ApiException ex) {
            return ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for concurrent test", ex);
        }
    }

    private record Fixture(PlayerProfile player,
                           Mission mission,
                           UUID[] questionIds,
                           UUID[] correctOptionIds,
                           UUID[] incorrectOptionIds) {
    }
}
