package com.vietsuuky.quiz.repository;

import com.vietsuuky.quiz.entity.QuizMissionQuestion;
import com.vietsuuky.quiz.id.QuizMissionQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizMissionQuestionRepository
        extends JpaRepository<QuizMissionQuestion, QuizMissionQuestionId> {
    List<QuizMissionQuestion> findById_MissionIdOrderById_QuestionOrderAsc(UUID missionId);
}
