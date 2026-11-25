package com.vietsuuky.quiz.repository;

import com.vietsuuky.quiz.entity.QuizAnswerEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAnswerEvidenceRepository extends JpaRepository<QuizAnswerEvidence, UUID> {
    List<QuizAnswerEvidence> findBySessionIdOrderByQuestionOrderAsc(UUID sessionId);

    Optional<QuizAnswerEvidence> findBySessionIdAndEventId(UUID sessionId, UUID eventId);

    Optional<QuizAnswerEvidence> findBySessionIdAndQuestionIdAndQuestionVersion(
            UUID sessionId, UUID questionId, Integer questionVersion);

    long countBySessionId(UUID sessionId);
}
