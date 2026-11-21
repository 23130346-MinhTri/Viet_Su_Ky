package com.vietsuuky.reward.service;

import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.reward.dto.PlayerRewardResponse;
import com.vietsuuky.reward.entity.PlayerReward;
import com.vietsuuky.reward.enums.RewardType;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PlayerRewardService {

    private final PlayerRewardRepository playerRewardRepository;

    public PlayerRewardService(PlayerRewardRepository playerRewardRepository) {
        this.playerRewardRepository = playerRewardRepository;
    }

    @Transactional
    public PlayerReward awardMissionReward(PlayerProfile player, Mission mission) {
        if (mission == null) {
            throw new ApiException("Mission is required");
        }
        if (playerRewardRepository.existsByPlayerIdAndMissionIdAndRewardType(player.getId(), mission.getId(), RewardType.MISSION_COMPLETE)) {
            return playerRewardRepository.findAll().stream()
                    .filter(reward -> reward.getPlayer().getId().equals(player.getId())
                            && reward.getMission().getId().equals(mission.getId())
                            && reward.getRewardType() == RewardType.MISSION_COMPLETE)
                    .findFirst()
                    .orElse(null);
        }
        PlayerReward reward = new PlayerReward(player, mission, RewardType.MISSION_COMPLETE, mission.getRewardPoints(), mission.getRewardCode());
        return playerRewardRepository.save(reward);
    }

    public List<PlayerRewardResponse> getRewardsForPlayer(UUID playerId) {
        return playerRewardRepository.findByPlayerIdOrderByClaimedAtDesc(playerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PlayerRewardResponse toResponse(PlayerReward reward) {
        return new PlayerRewardResponse(
                reward.getId(),
                reward.getMission().getId(),
                reward.getMission().getTitle(),
                reward.getRewardType(),
                reward.getRewardPoints(),
                reward.getRewardCode(),
                reward.getStatus(),
                reward.getClaimedAt()
        );
    }
}
