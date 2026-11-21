package com.vietsuuky.save.dto;

import java.time.Instant;
import java.util.UUID;

public record GameSaveResponse(
        UUID id,
        UUID missionId,
        UUID sessionId,
        String saveName,
        String saveData,
        int saveSize,
        Instant createdAt,
        Instant updatedAt
) {
}
