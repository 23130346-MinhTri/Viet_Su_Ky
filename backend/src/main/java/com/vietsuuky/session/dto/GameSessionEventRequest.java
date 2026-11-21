package com.vietsuuky.session.dto;

import jakarta.validation.constraints.NotBlank;

public record GameSessionEventRequest(
        @NotBlank(message = "eventType is required") String eventType,
        String payload
) {
}
