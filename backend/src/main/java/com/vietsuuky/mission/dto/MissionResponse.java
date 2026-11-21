package com.vietsuuky.mission.dto;

import com.vietsuuky.mission.enums.MissionStatus;
import com.vietsuuky.mission.enums.MissionType;

import java.util.UUID;

public record MissionResponse(
        UUID id,
        String code,
        String title,
        String description,
        String chapter,
        Integer missionOrder,
        MissionType missionType,
        String unlockCondition,
        UUID prerequisiteMissionId,
        int rewardPoints,
        String rewardCode,
        MissionStatus status
) {
}
