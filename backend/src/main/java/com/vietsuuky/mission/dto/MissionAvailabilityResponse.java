package com.vietsuuky.mission.dto;

import java.util.UUID;

public record MissionAvailabilityResponse(
        UUID missionId,
        String status,
        boolean available,
        String reason
) {
}
