package com.vietsuuky.quiz.dto;

import java.util.UUID;

public record QuizOptionResponse(
        UUID optionId,
        String label,
        String text
) {
}
