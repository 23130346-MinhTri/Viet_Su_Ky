package com.vietsuuky.quiz.repository;

import com.vietsuuky.quiz.entity.QuizQuestion;
import com.vietsuuky.quiz.id.QuizQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, QuizQuestionId> {
    Optional<QuizQuestion> findById_IdAndId_Version(UUID id, Integer version);
}
