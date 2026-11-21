package com.vietsuuky;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.service.AccountService;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.reward.entity.PlayerReward;
import com.vietsuuky.reward.enums.RewardType;
import com.vietsuuky.save.entity.GameSave;
import com.vietsuuky.save.repository.GameSaveRepository;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.entity.IdempotencyRecord;
import com.vietsuuky.session.enums.IdempotencyStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import com.vietsuuky.session.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class Phase24FoundationIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private PlayerProfileService playerProfileService;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private GameSaveRepository gameSaveRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void foundationDomainModelsPersistWithApprovedEnumsAndIdempotencySupport() {
        Account account = accountService.createAccount("foundation-phase24@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Foundation Test Player");

        Mission mission = missionRepository.save(new Mission("PHASE24-QUIZ", "Phase 24 Quiz", "CHAPTER_1", 1, MissionType.QUIZ, 100));
        assertEquals(MissionType.QUIZ, mission.getMissionType());

        GameSession session = gameSessionRepository.save(new GameSession(player, mission));
        assertNotNull(session.getVersion());

        GameSave save = gameSaveRepository.save(new GameSave(player, "autosave", "{\"status\":\"start\"}"));
        save.setMission(mission);
        save.setSession(session);
        GameSave saved = gameSaveRepository.save(save);
        assertNotNull(saved.getId());
        assertNotNull(saved.getVersion());

        IdempotencyRecord record = new IdempotencyRecord();
        record.setPlayer(player);
        record.setMission(mission);
        record.setSession(session);
        record.setOperationName("MISSION_COMPLETION");
        record.setIdempotencyKey("player-1-session-1");
        record.setRequestHash("hash-123");
        record.setStatus(IdempotencyStatus.PENDING);

        IdempotencyRecord persisted = idempotencyRecordRepository.save(record);
        assertNotNull(persisted.getId());
        assertEquals("MISSION_COMPLETION", persisted.getOperationName());
    }

    @Test
    void repeatableRewardsRequireSourceReference() {
        Account account = accountService.createAccount("reward-validation@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Reward Validation Player");
        Mission mission = missionRepository.save(new Mission("PHASE24-REWARD", "Phase 24 Reward", "CHAPTER_1", 2, MissionType.QUIZ, 100));

        PlayerReward reward = new PlayerReward(player, mission, RewardType.EVENT_REWARD, 25, "EVENT-REWARD");
        reward.setRewardSource("EVENT");
        reward.setSourceReference(null);

        assertThrows(IllegalArgumentException.class, reward::validateRewardPolicy);
    }

    @Test
    void gameSavePersistsUpdatesAndRejectsStaleVersions() {
        String suffix = UUID.randomUUID().toString();
        Account account = accountService.createAccount("game-save-" + suffix + "@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Game Save Test Player");

        GameSave inserted = gameSaveRepository.saveAndFlush(new GameSave(player, "autosave-" + suffix, "initial"));
        assertNotNull(inserted.getId());
        assertEquals(0, inserted.getVersion());

        GameSave readBack = gameSaveRepository.findById(inserted.getId()).orElseThrow();
        assertEquals("initial", readBack.getSaveData());

        GameSave stale = gameSaveRepository.findById(inserted.getId()).orElseThrow();
        GameSave current = gameSaveRepository.findById(inserted.getId()).orElseThrow();
        current.setSaveData("updated");
        current.setSaveSize(current.getSaveData().length());
        GameSave updated = gameSaveRepository.saveAndFlush(current);
        assertEquals(stale.getVersion() + 1, updated.getVersion());

        stale.setSaveData("stale update");
        stale.setSaveSize(stale.getSaveData().length());
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> gameSaveRepository.saveAndFlush(stale));

        GameSave persisted = gameSaveRepository.findById(inserted.getId()).orElseThrow();
        assertEquals("updated", persisted.getSaveData());
        assertEquals(updated.getVersion(), persisted.getVersion());
    }
}
