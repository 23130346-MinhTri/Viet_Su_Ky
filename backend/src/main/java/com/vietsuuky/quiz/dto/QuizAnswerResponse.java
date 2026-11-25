package com.vietsuuky.quiz.dto;

import java.util.UUID;

public record QuizAnswerResponse(
        UUID sessionId,
        UUID eventId,
        UUID questionId,
        UUID selectedOptionId,
        Integer answeredCount,
        boolean answerRecorded
) {
}
