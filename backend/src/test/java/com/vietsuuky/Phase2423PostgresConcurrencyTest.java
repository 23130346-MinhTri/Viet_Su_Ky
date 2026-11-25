package com.vietsuuky;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.repository.AccountRepository;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import com.vietsuuky.progress.enums.MissionProgressStatus;
import com.vietsuuky.progress.repository.PlayerProgressRepository;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import com.vietsuuky.save.repository.GameSaveRepository;
import com.vietsuuky.session.dto.CompleteGameSessionRequest;
import com.vietsuuky.session.dto.GameSessionEventRequest;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import com.vietsuuky.session.repository.IdempotencyRecordRepository;
import com.vietsuuky.session.service.GameSessionExpirationScheduler;
import com.vietsuuky.session.service.GameSessionService;
import com.vietsuuky.testsupport.TestDatabaseCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "POSTGRES_INTEGRATION_TESTS", matches = "true")
class Phase2423PostgresConcurrencyTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredPostgresEnvironment("POSTGRES_INTEGRATION_URL"));
        registry.add("spring.datasource.username", () -> requiredPostgresEnvironment("POSTGRES_INTEGRATION_USER"));
        registry.add("spring.datasource.password", () -> requiredPostgresEnvironment("POSTGRES_INTEGRATION_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "app");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.schemas", () -> "app");
        registry.add("spring.flyway.default-schema", () -> "app");
        registry.add("app.jwt.secret", () -> "01234567890123456789012345678901234567890123456789");
    }

    private static String requiredPostgresEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for POSTGRES_INTEGRATION_TESTS");
        }
        return value;
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private GameSaveRepository gameSaveRepository;

    @Autowired
    private MissionProgressRepository missionProgressRepository;

    @Autowired
    private PlayerProgressRepository playerProgressRepository;

    @Autowired
    private PlayerRewardRepository playerRewardRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private GameSessionService gameSessionService;

    @Autowired
    private GameSessionExpirationScheduler expirationScheduler;

    @BeforeEach
    void clearDatabase() {
        TestDatabaseCleanup.clearPlayerGraph(
                accountRepository,
                playerProfileRepository,
                gameSessionRepository,
                gameSaveRepository,
                missionProgressRepository,
                playerProgressRepository,
                playerRewardRepository,
                idempotencyRecordRepository);
    }

    @Test
    void eventVersusExpirationSchedulerEndsAsExpiredWithoutEvent() throws Exception {
        ConcurrencyFixture fixture = createOverdueFixture("CONC-EVENT");
        runTwoWorkers(
                () -> gameSessionService.recordEvent(
                        fixture.player().getId(),
                        fixture.session().getId(),
                        new GameSessionEventRequest("CHECKPOINT_REACHED", "{}")),
                expirationScheduler::expireOverdueSessions);

        GameSession persisted = gameSessionRepository.findById(fixture.session().getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, persisted.getStatus());
        assertNotNull(persisted.getEndedAt());
        assertEquals(0, persisted.getEventCount());
    }

    @Test
    void completionVersusExpirationSchedulerEndsExpiredWithoutReward() throws Exception {
        ConcurrencyFixture fixture = createOverdueFixture("CONC-COMPLETE");
        runTwoWorkers(
                () -> gameSessionService.completeSession(
                        fixture.player().getId(),
                        fixture.session().getId(),
                        new CompleteGameSessionRequest(100, "{}", 120)),
                expirationScheduler::expireOverdueSessions);

        GameSession persisted = gameSessionRepository.findById(fixture.session().getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, persisted.getStatus());
        assertNotNull(persisted.getEndedAt());
        assertEquals(0, persisted.getFinalScore());
        assertTrue(playerRewardRepository
                .findByPlayerIdOrderByClaimedAtDesc(fixture.player().getId()).isEmpty());
        var missionProgress = missionProgressRepository
                .findByPlayerIdAndMissionId(
                        fixture.player().getId(),
                        fixture.mission().getId());
        assertTrue(missionProgress.isEmpty()
                || missionProgress.orElseThrow().getStatus() != MissionProgressStatus.COMPLETED);
    }

    @Test
    void abandonmentVersusExpirationSchedulerEndsExpired() throws Exception {
        ConcurrencyFixture fixture = createOverdueFixture("CONC-ABANDON");
        runTwoWorkers(
                () -> gameSessionService.abandonSession(
                        fixture.player().getId(),
                        fixture.session().getId()),
                expirationScheduler::expireOverdueSessions);

        GameSession persisted = gameSessionRepository.findById(fixture.session().getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, persisted.getStatus());
        assertNotNull(persisted.getEndedAt());
    }

    @Test
    void concurrentExpirationAttemptsKeepOneStableTerminalTransition() throws Exception {
        ConcurrencyFixture fixture = createOverdueFixture("CONC-TWICE");
        runTwoWorkers(
                expirationScheduler::expireOverdueSessions,
                () -> gameSessionService.getSessionForPlayer(
                        fixture.player().getId(),
                        fixture.session().getId()));

        GameSession persisted = gameSessionRepository.findById(fixture.session().getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, persisted.getStatus());
        assertNotNull(persisted.getEndedAt());
        Instant endedAt = persisted.getEndedAt();

        assertThrows(ApiException.class,
                () -> gameSessionService.recordEvent(
                        fixture.player().getId(),
                        fixture.session().getId(),
                        new GameSessionEventRequest("CHECKPOINT_REACHED", "{}")));
        assertThrows(ApiException.class,
                () -> gameSessionService.completeSession(
                        fixture.player().getId(),
                        fixture.session().getId(),
                        new CompleteGameSessionRequest(100, "{}", 120)));

        GameSession terminal = gameSessionRepository.findById(fixture.session().getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, terminal.getStatus());
        assertEquals(endedAt, terminal.getEndedAt());
        assertTrue(playerRewardRepository
                .findByPlayerIdOrderByClaimedAtDesc(fixture.player().getId()).isEmpty());
    }

    private ConcurrencyFixture createOverdueFixture(String suffix) {
        String unique = UUID.randomUUID().toString();
        Account account = accountRepository.saveAndFlush(
                new Account("phase2423-" + unique + "@test.com", "test-password-hash"));
        PlayerProfile player = playerProfileRepository.saveAndFlush(
                new PlayerProfile(account, "Phase2423 " + suffix));
        Mission mission = missionRepository.saveAndFlush(new Mission(
                "PHASE2423-" + suffix + "-" + unique,
                "Concurrency mission",
                "CHAPTER_1",
                1,
                MissionType.EXPLORATION,
                10));
        GameSession session = gameSessionRepository.saveAndFlush(
                new GameSession(player, mission, Instant.now().minus(121, ChronoUnit.MINUTES)));
        session.setLastActivityAt(Instant.now().minus(31, ChronoUnit.MINUTES));
        GameSession saved = gameSessionRepository.saveAndFlush(session);
        return new ConcurrencyFixture(player, mission, saved);
    }

    private void runTwoWorkers(RunnableWorker first, RunnableWorker second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> one = executor.submit(() -> runWorker(first, ready, start));
            Future<?> two = executor.submit(() -> runWorker(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void runWorker(RunnableWorker worker,
                                  CountDownLatch ready,
                                  CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            worker.run();
        } catch (ApiException expectedLifecycleError) {
            // The operation may expire and reject atomically; the database assertion decides the result.
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for concurrency start", ex);
        }
    }

    @FunctionalInterface
    private interface RunnableWorker {
        void run();
    }

    private record ConcurrencyFixture(PlayerProfile player,
                                      Mission mission,
                                      GameSession session) {
    }
}
