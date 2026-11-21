package com.vietsuuky.progress.service;

import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.progress.entity.MissionProgress;
import com.vietsuuky.progress.enums.MissionProgressStatus;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MissionProgressService {

    private final MissionProgressRepository missionProgressRepository;

    public MissionProgressService(MissionProgressRepository missionProgressRepository) {
        this.missionProgressRepository = missionProgressRepository;
    }

    public MissionProgress getForPlayerAndMission(UUID playerId, UUID missionId) {
        return missionProgressRepository.findByPlayerIdAndMissionId(playerId, missionId)
                .orElseThrow(() -> new ApiException("Mission progress not found"));
    }

    @Transactional
    public MissionProgress ensureForPlayer(PlayerProfile player, Mission mission) {
        return missionProgressRepository.findByPlayerIdAndMissionId(player.getId(), mission.getId())
                .orElseGet(() -> missionProgressRepository.save(new MissionProgress(player, mission)));
    }

    @Transactional
    public MissionProgress markStarted(PlayerProfile player, Mission mission) {
        MissionProgress progress = ensureForPlayer(player, mission);
        progress.setStatus(MissionProgressStatus.IN_PROGRESS);
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(Instant.now());
        }
        progress.setProgressValue(0);
        progress.setLastEventAt(Instant.now());
        return missionProgressRepository.save(progress);
    }

    @Transactional
    public MissionProgress markCompleted(PlayerProfile player, Mission mission) {
        MissionProgress progress = missionProgressRepository.findForUpdateByPlayerIdAndMissionId(player.getId(), mission.getId())
                .orElseGet(() -> missionProgressRepository.save(new MissionProgress(player, mission)));

        progress.setStatus(MissionProgressStatus.COMPLETED);
        progress.setProgressValue(100);
        progress.setCompletedAt(Instant.now());
        progress.setLastEventAt(Instant.now());
        return missionProgressRepository.save(progress);
    }

    public List<MissionProgress> findByPlayer(UUID playerId) {
        return missionProgressRepository.findByPlayerIdOrderByMission_MissionOrderAsc(playerId);
    }
}
