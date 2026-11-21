package com.vietsuuky.save.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GameSaveRequest(
        @NotBlank(message = "saveName is required") String saveName,
        @NotBlank(message = "saveData is required") String saveData,
        UUID missionId,
        UUID sessionId
) {
}
