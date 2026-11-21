package com.vietsuuky.mission.service;

import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.dto.MissionAvailabilityResponse;
import com.vietsuuky.mission.dto.MissionResponse;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionStatus;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import com.vietsuuky.progress.entity.MissionProgress;
import com.vietsuuky.progress.enums.MissionProgressStatus;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionProgressRepository missionProgressRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public MissionService(MissionRepository missionRepository,
                         MissionProgressRepository missionProgressRepository,
                         PlayerProfileRepository playerProfileRepository) {
        this.missionRepository = missionRepository;
        this.missionProgressRepository = missionProgressRepository;
        this.playerProfileRepository = playerProfileRepository;
    }

    public List<MissionResponse> getMissionResponsesForPlayer(UUID playerId) {
        List<Mission> missions = missionRepository.findAllByOrderByMissionOrderAsc();
        Map<UUID, MissionProgress> progressMap = missionProgressRepository.findByPlayerIdOrderByMission_MissionOrderAsc(playerId)
                .stream()
                .collect(Collectors.toMap(progress -> progress.getMission().getId(), Function.identity()));
        List<MissionResponse> responses = new ArrayList<>();
        for (Mission mission : missions) {
            if (mission.getStatus() != MissionStatus.ACTIVE) {
                continue;
            }
            responses.add(toResponse(mission));
        }
        return responses;
    }

    public Mission getMissionOrThrow(UUID missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException("Mission not found"));
    }

    public MissionResponse getMissionResponse(UUID missionId) {
        return toResponse(getMissionOrThrow(missionId));
    }

    public MissionAvailabilityResponse getAvailabilityForPlayer(UUID playerId, UUID missionId) {
        Mission mission = getMissionOrThrow(missionId);
        if (mission.getStatus() != MissionStatus.ACTIVE) {
            return new MissionAvailabilityResponse(missionId, MissionProgressStatus.LOCKED.name(), false, "Mission is not active");
        }

        MissionProgressStatus status = resolveStatus(playerId, mission);
        String reason = status == MissionProgressStatus.AVAILABLE ? "Mission is available" : "Mission is locked";
        return new MissionAvailabilityResponse(missionId, status.name(), status != MissionProgressStatus.LOCKED, reason);
    }

    public void ensureMissionAvailable(UUID playerId, UUID missionId) {
        MissionAvailabilityResponse availability = getAvailabilityForPlayer(playerId, missionId);
        if (!availability.available()) {
            throw new ApiException("Mission is locked or unavailable for this player");
        }
    }

    public void ensureMissionAvailable(PlayerProfile player, Mission mission) {
        ensureMissionAvailable(player.getId(), mission.getId());
    }

    public MissionProgressStatus resolveStatus(UUID playerId, Mission mission) {
        MissionProgress progress = missionProgressRepository.findByPlayerIdAndMissionId(playerId, mission.getId()).orElse(null);
        if (progress != null) {
            return progress.getStatus();
        }

        if (mission.getPrerequisiteMission() != null) {
            Mission prerequisite = mission.getPrerequisiteMission();
            MissionProgress prerequisiteProgress = missionProgressRepository.findByPlayerIdAndMissionId(playerId, prerequisite.getId()).orElse(null);
            if (prerequisiteProgress == null || prerequisiteProgress.getStatus() != MissionProgressStatus.COMPLETED) {
                return MissionProgressStatus.LOCKED;
            }
        }

        return MissionProgressStatus.AVAILABLE;
    }

    public MissionResponse toResponse(Mission mission) {
        return new MissionResponse(
                mission.getId(),
                mission.getCode(),
                mission.getTitle(),
                mission.getDescription(),
                mission.getChapter(),
                mission.getMissionOrder(),
                mission.getMissionType(),
                mission.getUnlockCondition(),
                mission.getPrerequisiteMission() == null ? null : mission.getPrerequisiteMission().getId(),
                mission.getRewardPoints(),
                mission.getRewardCode(),
                mission.getStatus()
        );
    }
}
