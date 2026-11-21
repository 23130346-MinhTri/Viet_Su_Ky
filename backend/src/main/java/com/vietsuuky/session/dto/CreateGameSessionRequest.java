package com.vietsuuky.session.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateGameSessionRequest(
        @NotNull(message = "missionId is required") UUID missionId
) {
}
