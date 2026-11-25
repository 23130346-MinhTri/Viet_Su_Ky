package com.vietsuuky.session.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CompleteGameSessionRequest(
        Integer finalScore,
        String metadata,
        Integer durationSeconds
) {
}
