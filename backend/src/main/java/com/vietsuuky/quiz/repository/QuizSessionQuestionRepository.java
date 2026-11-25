package com.vietsuuky.quiz.repository;

import com.vietsuuky.quiz.entity.QuizSessionQuestion;
import com.vietsuuky.quiz.id.QuizSessionQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizSessionQuestionRepository
        extends JpaRepository<QuizSessionQuestion, QuizSessionQuestionId> {
    List<QuizSessionQuestion> findById_SessionIdOrderById_QuestionOrderAsc(UUID sessionId);

    Optional<QuizSessionQuestion> findById_SessionIdAndQuestionId(
            UUID sessionId, UUID questionId);
}
