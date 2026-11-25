package com.vietsuuky.quiz.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QuizAnswerRequest(
        @NotNull UUID eventId,
        @NotNull UUID questionId,
        @NotNull UUID selectedOptionId
) {
}
