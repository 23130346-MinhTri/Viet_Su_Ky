package com.vietsuuky.session.dto;

import com.vietsuuky.session.enums.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record GameSessionResponse(
        UUID id,
        UUID missionId,
        String missionCode,
        String missionTitle,
        UUID playerId,
        Instant startedAt,
        Instant endedAt,
        Instant lastActivityAt,
        SessionStatus status,
        int eventCount,
        int finalScore,
        String metadata
) {
}
