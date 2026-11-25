package com.vietsuuky.quiz.entity;

import com.vietsuuky.quiz.id.QuizSessionQuestionId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_session_questions", schema = "app")
public class QuizSessionQuestion {
    @EmbeddedId
    private QuizSessionQuestionId id;

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question_version", nullable = false)
    private Integer questionVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public QuizSessionQuestion() {
    }

    public QuizSessionQuestion(QuizSessionQuestionId id, UUID missionId, UUID questionId,
                               Integer questionVersion) {
        this.id = id;
        this.missionId = missionId;
        this.questionId = questionId;
        this.questionVersion = questionVersion;
    }

    public QuizSessionQuestionId getId() {
        return id;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public Integer getQuestionVersion() {
        return questionVersion;
    }
}
