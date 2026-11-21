package com.vietsuuky.progress.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionStatus;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.mission.service.MissionService;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import com.vietsuuky.progress.dto.MissionProgressResponse;
import com.vietsuuky.progress.dto.PlayerProgressResponse;
import com.vietsuuky.progress.entity.MissionProgress;
import com.vietsuuky.progress.entity.PlayerProgress;
import com.vietsuuky.progress.enums.MissionProgressStatus;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import com.vietsuuky.progress.repository.PlayerProgressRepository;
import com.vietsuuky.reward.entity.PlayerReward;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlayerProgressService {

    private final PlayerProgressRepository playerProgressRepository;
    private final MissionProgressRepository missionProgressRepository;
    private final MissionRepository missionRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MissionService missionService;
    private final PlayerRewardRepository playerRewardRepository;
    private final ObjectMapper objectMapper;

    public PlayerProgressService(PlayerProgressRepository playerProgressRepository,
                                MissionProgressRepository missionProgressRepository,
                                MissionRepository missionRepository,
                                PlayerProfileRepository playerProfileRepository,
                                MissionService missionService,
                                PlayerRewardRepository playerRewardRepository,
                                ObjectMapper objectMapper) {
        this.playerProgressRepository = playerProgressRepository;
        this.missionProgressRepository = missionProgressRepository;
        this.missionRepository = missionRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.missionService = missionService;
        this.playerRewardRepository = playerRewardRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PlayerProgress ensureForPlayer(PlayerProfile player) {
        return playerProgressRepository.findByPlayerId(player.getId())
                .orElseGet(() -> playerProgressRepository.save(new PlayerProgress(player)));
    }

    @Transactional
    public PlayerProgress recalculateForPlayer(PlayerProfile player) {
        List<MissionProgress> missionProgress = missionProgressRepository.findByPlayerIdOrderByMission_MissionOrderAsc(player.getId());
        int totalCompleted = (int) missionProgress.stream()
                .filter(progress -> progress.getStatus() == MissionProgressStatus.COMPLETED)
                .count();

        List<String> unlocked = missionRepository.findAllByOrderByMissionOrderAsc().stream()
                .filter(mission -> mission.getStatus() == MissionStatus.ACTIVE)
                .filter(mission -> missionService.resolveStatus(player.getId(), mission) != MissionProgressStatus.LOCKED)
                .map(Mission::getCode)
                .toList();

        int totalPoints = playerRewardRepository.findByPlayerIdOrderByClaimedAtDesc(player.getId()).stream()
                .mapToInt(PlayerReward::getRewardPoints)
                .sum();

        PlayerProgress progress = ensureForPlayer(player);
        progress.setChapter("CHAPTER_1");
        progress.setTotalMissionsCompleted(totalCompleted);
        progress.setTotalPoints(totalPoints);
        try {
            progress.setUnlockedContent(objectMapper.writeValueAsString(unlocked));
        } catch (Exception ex) {
            progress.setUnlockedContent("[]");
        }
        return playerProgressRepository.save(progress);
    }

    public PlayerProgressResponse getPlayerProgress(UUID playerId) {
        PlayerProfile player = playerProfileRepository.findById(playerId)
                .orElseThrow(() -> new ApiException("Player not found"));
        PlayerProgress progress = ensureForPlayer(player);
        return new PlayerProgressResponse(
                playerId,
                progress.getChapter(),
                progress.getTotalMissionsCompleted(),
                progress.getTotalPoints(),
                parseUnlocked(progress.getUnlockedContent()),
                getMissionProgressResponses(playerId)
        );
    }

    public List<MissionProgressResponse> getMissionProgressResponses(UUID playerId) {
        List<Mission> missions = missionRepository.findAllByOrderByMissionOrderAsc();
        Map<UUID, MissionProgress> progressMap = missionProgressRepository.findByPlayerIdOrderByMission_MissionOrderAsc(playerId)
                .stream()
                .collect(Collectors.toMap(progress -> progress.getMission().getId(), Function.identity()));

        List<MissionProgressResponse> responses = new ArrayList<>();
        for (Mission mission : missions) {
            if (mission.getStatus() != MissionStatus.ACTIVE) {
                continue;
            }
            MissionProgress progress = progressMap.get(mission.getId());
            MissionProgressStatus status = progress != null ? progress.getStatus() : missionService.resolveStatus(playerId, mission);
            boolean available = status != MissionProgressStatus.LOCKED;
            responses.add(new MissionProgressResponse(
                    mission.getId(),
                    mission.getCode(),
                    mission.getTitle(),
                    mission.getChapter(),
                    mission.getMissionOrder(),
                    status,
                    progress == null ? 0 : progress.getProgressValue(),
                    progress == null ? null : progress.getStartedAt(),
                    progress == null ? null : progress.getCompletedAt(),
                    available
            ));
        }
        return responses;
    }

    private List<String> parseUnlocked(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("[]")) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }
}
