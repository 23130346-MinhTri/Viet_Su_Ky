package com.vietsuuky;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.service.AccountService;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionType;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.mission.service.MissionService;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.progress.entity.MissionProgress;
import com.vietsuuky.progress.enums.MissionProgressStatus;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import com.vietsuuky.progress.service.PlayerProgressService;
import com.vietsuuky.reward.enums.RewardType;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import com.vietsuuky.session.dto.CompleteGameSessionRequest;
import com.vietsuuky.session.dto.GameSessionEventRequest;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import com.vietsuuky.session.service.GameSessionService;
import com.vietsuuky.session.service.GameSessionExpirationScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    @Autowired
    private MissionService missionService;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private GameSessionExpirationScheduler expirationScheduler;

    @Test
    void createAndCompleteMissionAwardRewardAndUpdateProgress() {
        Account account = accountService.createAccount("player-phase24@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Phase24 Player");

        Mission mission = missionRepository.save(new Mission("M-001", "First Mission", "CHAPTER_1", 1, MissionType.QUIZ, 250));

        GameSession session = gameSessionService.createSession(player.getId(), mission.getId());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertTrue(missionProgressRepository
                .findByPlayerIdAndMissionId(player.getId(), mission.getId()).isPresent());

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

    @Test
    void rejectsDuplicateActiveSessionForSamePlayerAndMission() {
        Account account = accountService.createAccount("duplicate-session@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Duplicate Session Player");
        Mission mission = missionRepository.save(new Mission(
                "M-DUPLICATE", "Duplicate Session", "CHAPTER_1", 3, MissionType.QUIZ, 100));

        gameSessionService.createSession(player.getId(), mission.getId());

        ApiException conflict = assertThrows(ApiException.class,
                () -> gameSessionService.createSession(player.getId(), mission.getId()));
        assertEquals("SESSION_ALREADY_ACTIVE", conflict.getErrorCode());
    }

    @Test
    void rejectsLockedMissionSession() {
        Account account = accountService.createAccount("locked-session@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Locked Session Player");
        Mission prerequisite = missionRepository.save(new Mission(
                "M-PREREQ", "Prerequisite", "CHAPTER_1", 4, MissionType.QUIZ, 100));
        Mission locked = missionRepository.save(new Mission(
                "M-LOCKED", "Locked Mission", "CHAPTER_1", 5, MissionType.QUIZ, 100));
        locked.setPrerequisiteMission(prerequisite);
        missionRepository.save(locked);

        ApiException conflict = assertThrows(ApiException.class,
                () -> gameSessionService.createSession(player.getId(), locked.getId()));
        assertEquals("MISSION_LOCKED", conflict.getErrorCode());
        assertEquals(0, missionProgressRepository
                .findByPlayerIdAndMissionId(player.getId(), locked.getId()).stream().count());
    }

    @Test
    void missionGetDoesNotCreateMissingProgress() {
        Account account = accountService.createAccount("mission-read@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Mission Read Player");
        Mission mission = missionRepository.save(new Mission(
                "M-READ", "Read-only Mission", "CHAPTER_1", 6, MissionType.QUIZ, 100));

        var responses = missionService.getMissionResponsesForPlayer(player.getId());

        assertTrue(responses.stream().anyMatch(response ->
                response.id().equals(mission.getId())
                        && response.available()
                        && response.progressStatus() == MissionProgressStatus.AVAILABLE));
        assertTrue(missionProgressRepository
                .findByPlayerIdAndMissionId(player.getId(), mission.getId()).isEmpty());
    }

    @Test
    void getRetrievalExpiresInactivityOverdueSession() {
        Account account = accountService.createAccount("expiration-get@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Expiration Get Player");
        Mission mission = missionRepository.save(new Mission(
                "M-EXP-GET", "Expiration Get", "CHAPTER_1", 7, MissionType.QUIZ, 100));
        GameSession session = gameSessionService.createSession(player.getId(), mission.getId());
        session.setLastActivityAt(Instant.now().minus(31, ChronoUnit.MINUTES));
        gameSessionRepository.saveAndFlush(session);

        GameSession expired = gameSessionService.getSessionForPlayer(player.getId(), session.getId());

        assertEquals(SessionStatus.EXPIRED, expired.getStatus());
        assertTrue(expired.getEndedAt() != null);

        GameSession persisted = gameSessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, persisted.getStatus());
        assertTrue(persisted.getEndedAt() != null);
    }

    @Test
    void eventAndCompletionRejectOverdueSessionWithoutReward() {
        Account account = accountService.createAccount("expiration-event@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Expiration Event Player");
        Mission mission = missionRepository.save(new Mission(
                "M-EXP-EVENT", "Expiration Event", "CHAPTER_1", 8, MissionType.QUIZ, 100));
        GameSession session = gameSessionService.createSession(player.getId(), mission.getId());
        session.setStartedAt(Instant.now().minus(121, ChronoUnit.MINUTES));
        session.setLastActivityAt(session.getStartedAt());
        gameSessionRepository.saveAndFlush(session);

        ApiException eventConflict = assertThrows(ApiException.class,
                () -> gameSessionService.recordEvent(
                        player.getId(), session.getId(),
                        new GameSessionEventRequest("CHECKPOINT_REACHED", "{}")));
        assertEquals("SESSION_EXPIRED", eventConflict.getErrorCode());

        ApiException completionConflict = assertThrows(ApiException.class,
                () -> gameSessionService.completeSession(
                        player.getId(), session.getId(),
                        new CompleteGameSessionRequest(100, "{}", 120)));
        assertEquals("SESSION_EXPIRED", completionConflict.getErrorCode());

        GameSession expired = gameSessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, expired.getStatus());
        assertTrue(expired.getEndedAt() != null);
        assertTrue(playerRewardRepository
                .findByPlayerIdOrderByClaimedAtDesc(player.getId()).isEmpty());
        var persistedProgress = missionProgressRepository
                .findByPlayerIdAndMissionId(player.getId(), mission.getId());
        assertTrue(persistedProgress.isEmpty()
                || persistedProgress.orElseThrow().getStatus() != MissionProgressStatus.COMPLETED);
    }

    @Test
    void abandonmentConvertsOverdueSessionToExpired() {
        Account account = accountService.createAccount("expiration-abandon@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Expiration Abandon Player");
        Mission mission = missionRepository.save(new Mission(
                "M-EXP-ABANDON", "Expiration Abandon", "CHAPTER_1", 9, MissionType.QUIZ, 100));
        GameSession session = gameSessionService.createSession(player.getId(), mission.getId());
        session.setLastActivityAt(Instant.now().minus(31, ChronoUnit.MINUTES));
        gameSessionRepository.saveAndFlush(session);

        GameSession expired = gameSessionService.abandonSession(player.getId(), session.getId());

        assertEquals(SessionStatus.EXPIRED, expired.getStatus());
        assertTrue(expired.getEndedAt() != null);

        GameSession persisted = gameSessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, persisted.getStatus());
        assertTrue(persisted.getEndedAt() != null);
    }

    @Test
    void validEventRefreshesActivityAndDoesNotExtendLifetime() {
        Account account = accountService.createAccount("expiration-event-valid@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Valid Event Player");
        Mission mission = missionRepository.save(new Mission(
                "M-EXP-VALID", "Valid Event", "CHAPTER_1", 10, MissionType.QUIZ, 100));
        GameSession session = gameSessionService.createSession(player.getId(), mission.getId());
        session.setStartedAt(Instant.now().minus(119, ChronoUnit.MINUTES));
        session.setLastActivityAt(Instant.now().minus(29, ChronoUnit.MINUTES));
        Instant lifetimeBeforeEvent = session.getStartedAt();
        gameSessionRepository.saveAndFlush(session);

        GameSession updated = gameSessionService.recordEvent(
                player.getId(), session.getId(),
                new GameSessionEventRequest("CHECKPOINT_REACHED", "{}"));

        assertEquals(SessionStatus.ACTIVE, updated.getStatus());
        assertEquals(lifetimeBeforeEvent.truncatedTo(ChronoUnit.SECONDS),
                updated.getStartedAt().truncatedTo(ChronoUnit.SECONDS));
        assertTrue(updated.getLastActivityAt().isAfter(session.getLastActivityAt()));
    }

    @Test
    void scheduledSweepExpiresOverdueAndLeavesTerminalUntouched() {
        Account account = accountService.createAccount("expiration-sweep@test.com", "Password123!");
        PlayerProfile player = playerProfileService.createForAccount(account, "Expiration Sweep Player");
        Mission mission = missionRepository.save(new Mission(
                "M-EXP-SWEEP", "Expiration Sweep", "CHAPTER_1", 11, MissionType.QUIZ, 100));
        GameSession overdue = gameSessionService.createSession(player.getId(), mission.getId());
        overdue.setLastActivityAt(Instant.now().minus(31, ChronoUnit.MINUTES));
        gameSessionRepository.saveAndFlush(overdue);

        Mission terminalMission = missionRepository.save(new Mission(
                "M-EXP-TERMINAL", "Expiration Terminal", "CHAPTER_1", 12, MissionType.QUIZ, 100));
        GameSession abandoned = gameSessionService.createSession(player.getId(), terminalMission.getId());
        abandoned = gameSessionService.abandonSession(player.getId(), abandoned.getId());

        expirationScheduler.expireOverdueSessions();

        GameSession expired = gameSessionRepository.findById(overdue.getId()).orElseThrow();
        GameSession terminal = gameSessionRepository.findById(abandoned.getId()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, expired.getStatus());
        assertEquals(SessionStatus.ABANDONED, terminal.getStatus());
    }
}
