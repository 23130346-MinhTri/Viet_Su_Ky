package com.vietsuuky.quiz.dto;

public record QuizAnswerResult(
        QuizAnswerResponse response,
        boolean replayed
) {
}
