package com.vietsuuky.session.dto;

public record CompleteGameSessionRequest(
        Integer finalScore,
        String metadata,
        Integer durationSeconds
) {
}
