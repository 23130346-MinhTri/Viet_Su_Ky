package com.vietsuuky.progress.dto;

import java.util.List;
import java.util.UUID;

public record PlayerProgressResponse(
        UUID playerId,
        String chapter,
        int totalMissionsCompleted,
        int totalPoints,
        List<String> unlockedContent,
        List<MissionProgressResponse> missionProgress
) {
}
