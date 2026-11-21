package com.vietsuuky.testsupport;

import com.vietsuuky.account.repository.AccountRepository;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import com.vietsuuky.progress.repository.PlayerProgressRepository;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import com.vietsuuky.save.repository.GameSaveRepository;
import com.vietsuuky.session.repository.GameSessionRepository;
import com.vietsuuky.session.repository.IdempotencyRecordRepository;

public final class TestDatabaseCleanup {
    private TestDatabaseCleanup() {
    }

    public static void clearPlayerGraph(
            AccountRepository accountRepository,
            PlayerProfileRepository playerProfileRepository,
            GameSessionRepository gameSessionRepository,
            GameSaveRepository gameSaveRepository,
            MissionProgressRepository missionProgressRepository,
            PlayerProgressRepository playerProgressRepository,
            PlayerRewardRepository playerRewardRepository,
            IdempotencyRecordRepository idempotencyRecordRepository
    ) {
        idempotencyRecordRepository.deleteAll();
        playerRewardRepository.deleteAll();
        gameSaveRepository.deleteAll();
        missionProgressRepository.deleteAll();
        playerProgressRepository.deleteAll();
        gameSessionRepository.deleteAll();
        playerProfileRepository.deleteAll();
        accountRepository.deleteAll();
    }
}
