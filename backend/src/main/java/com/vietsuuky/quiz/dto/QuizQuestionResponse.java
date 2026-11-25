package com.vietsuuky.quiz.dto;

import java.util.List;
import java.util.UUID;

public record QuizQuestionResponse(
        UUID questionId,
        Integer order,
        String prompt,
        List<QuizOptionResponse> options,
        boolean answered,
        UUID selectedOptionId
) {
}
