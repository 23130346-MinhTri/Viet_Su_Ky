package com.vietsuuky;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.service.AccountService;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.progress.entity.MissionProgress;
import com.vietsuuky.progress.enums.MissionProgressStatus;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import com.vietsuuky.progress.service.PlayerProgressService;
import com.vietsuuky.reward.enums.RewardType;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import com.vietsuuky.session.dto.CompleteGameSessionRequest;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.service.GameSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class GameSessionServiceIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private PlayerProfileService playerProfileService;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private MissionProgressRepository missionProgressRepository;

    @Autowired
    private PlayerRewardRepository playerRewardRepository;

    @Autowired
    private PlayerProgressService playerProgressService;

    @Autowired
    private GameSessionService gameSessionService;

    @Test
    void createAndCompleteMissionAwardRewardAndUpdateProgress() {
        Account account = accountService.createAccount("player-phase24@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Phase24 Player");

        Mission mission = missionRepository.save(new Mission("M-001", "First Mission", "CHAPTER_1", 1, MissionType.QUIZ, 250));

        GameSession session = gameSessionService.createSession(player.getId(), mission.getId());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());

        gameSessionService.completeSession(player.getId(), session.getId(), new CompleteGameSessionRequest(95, "{\"result\":\"success\"}", 120));

        MissionProgress progress = missionProgressRepository.findByPlayerIdAndMissionId(player.getId(), mission.getId()).orElseThrow();
        assertEquals(MissionProgressStatus.COMPLETED, progress.getStatus());
        assertTrue(playerRewardRepository.existsByPlayerIdAndMissionIdAndRewardType(player.getId(), mission.getId(), RewardType.MISSION_COMPLETE));

        var playerProgress = playerProgressService.getPlayerProgress(player.getId());
        assertEquals(1, playerProgress.totalMissionsCompleted());
        assertTrue(playerProgress.totalPoints() >= 250);
    }

    @Test
    void cannotAccessSessionOfAnotherPlayer() {
        Account accountOne = accountService.createAccount("player-a@test.com", "Password123!");
        PlayerProfile playerOne = playerProfileService.createForAccount(accountOne, "Player A");
        Account accountTwo = accountService.createAccount("player-b@test.com", "Password123!");
        PlayerProfile playerTwo = playerProfileService.createForAccount(accountTwo, "Player B");

        Mission mission = missionRepository.save(new Mission("M-002", "Second Mission", "CHAPTER_1", 2, MissionType.QUIZ, 100));
        GameSession session = gameSessionService.createSession(playerOne.getId(), mission.getId());

        assertThrows(ApiException.class, () -> gameSessionService.getSessionForPlayer(playerTwo.getId(), session.getId()));
    }
}
