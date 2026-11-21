package com.vietsuuky.reward.dto;

import com.vietsuuky.reward.enums.RewardType;

import java.time.Instant;
import java.util.UUID;

public record PlayerRewardResponse(
        UUID id,
        UUID missionId,
        String missionTitle,
        RewardType rewardType,
        int rewardPoints,
        String rewardCode,
        String status,
        Instant claimedAt
) {
}
