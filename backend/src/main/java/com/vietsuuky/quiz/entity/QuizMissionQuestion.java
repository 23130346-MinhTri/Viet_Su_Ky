package com.vietsuuky.quiz.entity;

import com.vietsuuky.quiz.id.QuizMissionQuestionId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_mission_questions", schema = "app")
public class QuizMissionQuestion {
    @EmbeddedId
    private QuizMissionQuestionId id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question_version", nullable = false)
    private Integer questionVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public QuizMissionQuestion() {
    }

    public QuizMissionQuestion(QuizMissionQuestionId id, UUID questionId, Integer questionVersion) {
        this.id = id;
        this.questionId = questionId;
        this.questionVersion = questionVersion;
    }

    public QuizMissionQuestionId getId() {
        return id;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public Integer getQuestionVersion() {
        return questionVersion;
    }
}
