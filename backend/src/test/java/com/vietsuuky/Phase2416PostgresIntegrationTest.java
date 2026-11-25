package com.vietsuuky;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.repository.AccountRepository;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import com.vietsuuky.reward.entity.PlayerReward;
import com.vietsuuky.reward.enums.RewardType;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import com.vietsuuky.save.entity.GameSave;
import com.vietsuuky.save.repository.GameSaveRepository;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "POSTGRES_INTEGRATION_TESTS", matches = "true")
class Phase2416PostgresIntegrationTest {

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
    private MissionRepository missionRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private GameSaveRepository gameSaveRepository;

    @Autowired
    private PlayerRewardRepository playerRewardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void gameSessionPersistsAndOptimisticallyLocksUpdates() {
        PlayerProfile player = createPlayer();
        Mission mission = createMission();
        GameSession inserted = gameSessionRepository.saveAndFlush(new GameSession(player, mission));

        assertEquals(0, inserted.getVersion());
        GameSession readBack = gameSessionRepository.findById(inserted.getId()).orElseThrow();
        assertEquals(SessionStatus.ACTIVE, readBack.getStatus());
        assertEquals(0, readBack.getVersion());

        GameSession stale = gameSessionRepository.findById(inserted.getId()).orElseThrow();
        GameSession current = gameSessionRepository.findById(inserted.getId()).orElseThrow();
        current.setEventCount(1);
        GameSession updated = gameSessionRepository.saveAndFlush(current);

        assertEquals(1, updated.getVersion());
        assertEquals(SessionStatus.ACTIVE, updated.getStatus());
        stale.setEventCount(2);
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> gameSessionRepository.saveAndFlush(stale));

        GameSession persisted = gameSessionRepository.findById(inserted.getId()).orElseThrow();
        assertEquals(1, persisted.getEventCount());
        assertEquals(1, persisted.getVersion());
        assertEquals(SessionStatus.ACTIVE, persisted.getStatus());
    }

    @Test
    void gameSavePersistsAndOptimisticallyLocksUpdates() {
        PlayerProfile player = createPlayer();
        String saveName = "save-" + UUID.randomUUID();
        GameSave inserted = gameSaveRepository.saveAndFlush(new GameSave(player, saveName, "initial"));

        assertEquals(0, inserted.getVersion());
        GameSave readBack = gameSaveRepository.findById(inserted.getId()).orElseThrow();
        assertEquals("initial", readBack.getSaveData());

        GameSave stale = gameSaveRepository.findById(inserted.getId()).orElseThrow();
        GameSave current = gameSaveRepository.findById(inserted.getId()).orElseThrow();
        current.setSaveData("updated");
        current.setSaveSize(current.getSaveData().length());
        GameSave updated = gameSaveRepository.saveAndFlush(current);

        assertEquals(1, updated.getVersion());
        stale.setSaveData("stale update");
        stale.setSaveSize(stale.getSaveData().length());
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> gameSaveRepository.saveAndFlush(stale));

        GameSave persisted = gameSaveRepository.findById(inserted.getId()).orElseThrow();
        assertEquals("updated", persisted.getSaveData());
        assertEquals(1, persisted.getVersion());
    }

    @Test
    void playerRewardsPersistAndEnforceAllUniquePolicies() {
        PlayerProfile player = createPlayer();
        Mission firstMission = createMission();
        Mission secondMission = createMission();
        Mission thirdMission = createMission();
        GameSession session = gameSessionRepository.saveAndFlush(new GameSession(player, firstMission));

        PlayerReward missionReward = new PlayerReward(
                player, firstMission, RewardType.MISSION_COMPLETE, 10, "MISSION-REWARD");
        PlayerReward persistedMissionReward = playerRewardRepository.saveAndFlush(missionReward);
        PlayerReward readBack = playerRewardRepository.findById(persistedMissionReward.getId()).orElseThrow();
        assertEquals("CLAIMED", readBack.getStatus());
        assertEquals(10, readBack.getRewardPoints());
        assertThrows(DataAccessException.class,
                () -> playerRewardRepository.saveAndFlush(new PlayerReward(
                        player, firstMission, RewardType.MISSION_COMPLETE, 10, "DUPLICATE-MISSION")));

        String reference = "repeatable-" + UUID.randomUUID();
        PlayerReward repeatable = new PlayerReward(player, secondMission, RewardType.EVENT_REWARD, 5, "EVENT");
        repeatable.setRewardSource("EVENT");
        repeatable.setSourceReference(reference);
        playerRewardRepository.saveAndFlush(repeatable);

        PlayerReward duplicateReference = new PlayerReward(player, thirdMission, RewardType.EVENT_REWARD, 5, "EVENT");
        duplicateReference.setRewardSource("EVENT");
        duplicateReference.setSourceReference(reference);
        assertThrows(DataAccessException.class,
                () -> playerRewardRepository.saveAndFlush(duplicateReference));

        PlayerReward sessionReward = new PlayerReward(
                player, secondMission, RewardType.DAILY_BONUS, 1, "SESSION");
        sessionReward.setSession(session);
        playerRewardRepository.saveAndFlush(sessionReward);

        PlayerReward duplicateSessionReward = new PlayerReward(
                player, thirdMission, RewardType.DAILY_BONUS, 1, "SESSION");
        duplicateSessionReward.setSession(session);
        assertThrows(DataAccessException.class,
                () -> playerRewardRepository.saveAndFlush(duplicateSessionReward));
    }

    @Test
    void databaseRejectsMissingRepeatableReferencesAndInvalidMissionTypes() {
        PlayerProfile player = createPlayer();
        Mission mission = createMission();
        String[] repeatableSources = {"ACHIEVEMENT", "REPLAY", "EVENT", "OPTIONAL_MISSION"};
        String[] invalidReferences = {null, "", " \t\r\n"};

        for (String source : repeatableSources) {
            for (String reference : invalidReferences) {
                assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                        """
                        INSERT INTO app.player_rewards
                            (player_id, mission_id, reward_source, source_reference, reward_type)
                        VALUES (?, ?, ?, ?, 'EVENT_REWARD')
                        """,
                        player.getId(), mission.getId(), source, reference));
            }
        }

        String defaultMissionType = jdbcTemplate.queryForObject(
                """
                INSERT INTO app.missions (code, title, mission_order)
                VALUES (?, ?, ?)
                RETURNING mission_type
                """,
                String.class,
                "DEFAULT-" + UUID.randomUUID(),
                "Default type mission",
                999);
        assertEquals(MissionType.QUIZ.name(), defaultMissionType);

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO app.missions (code, title, mission_order, mission_type)
                VALUES (?, ?, ?, ?)
                """,
                "INVALID-" + UUID.randomUUID(),
                "Invalid type mission",
                1000,
                "NOT_A_MISSION"));
    }

    private PlayerProfile createPlayer() {
        String suffix = UUID.randomUUID().toString();
        Account account = accountRepository.saveAndFlush(
                new Account("phase2416-" + suffix + "@test.com", "test-password-hash"));
        return playerProfileRepository.saveAndFlush(
                new PlayerProfile(account, "Player " + suffix.substring(0, 8)));
    }

    private Mission createMission() {
        String suffix = UUID.randomUUID().toString();
        return missionRepository.saveAndFlush(new Mission(
                "PHASE2416-" + suffix,
                "Phase 2.4.1.6 test mission",
                "CHAPTER_1",
                1,
                MissionType.EXPLORATION,
                10));
    }
}
