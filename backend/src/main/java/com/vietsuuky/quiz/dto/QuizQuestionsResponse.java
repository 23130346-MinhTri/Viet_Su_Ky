package com.vietsuuky.quiz.dto;

import com.vietsuuky.session.enums.SessionStatus;

import java.util.List;
import java.util.UUID;

public record QuizQuestionsResponse(
        UUID sessionId,
        SessionStatus status,
        Integer answeredCount,
        List<QuizQuestionResponse> questions
) {
}
