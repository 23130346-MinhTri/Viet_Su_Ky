package com.vietsuuky.quiz.repository;

import com.vietsuuky.quiz.entity.QuizAnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAnswerOptionRepository extends JpaRepository<QuizAnswerOption, UUID> {
    List<QuizAnswerOption> findByQuestionIdAndQuestionVersionOrderByDisplayOrderAsc(
            UUID questionId, Integer questionVersion);

    Optional<QuizAnswerOption> findByIdAndQuestionIdAndQuestionVersion(
            UUID id, UUID questionId, Integer questionVersion);
}
