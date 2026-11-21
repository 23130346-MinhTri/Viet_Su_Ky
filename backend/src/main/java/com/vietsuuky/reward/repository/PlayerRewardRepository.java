package com.vietsuuky.reward.repository;

import com.vietsuuky.reward.entity.PlayerReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayerRewardRepository extends JpaRepository<PlayerReward, UUID> {
    List<PlayerReward> findByPlayerIdOrderByClaimedAtDesc(UUID playerId);
    boolean existsByPlayerIdAndMissionIdAndRewardType(UUID playerId, UUID missionId, com.vietsuuky.reward.enums.RewardType rewardType);
}
