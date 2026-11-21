package com.vietsuuky.progress.dto;

import com.vietsuuky.progress.enums.MissionProgressStatus;

import java.time.Instant;
import java.util.UUID;

public record MissionProgressResponse(
        UUID missionId,
        String code,
        String title,
        String chapter,
        Integer missionOrder,
        MissionProgressStatus status,
        int progressValue,
        Instant startedAt,
        Instant completedAt,
        boolean available
) {
}
