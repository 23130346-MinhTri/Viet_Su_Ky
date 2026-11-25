package com.vietsuuky.quiz.entity;

import com.vietsuuky.quiz.id.QuizQuestionId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "quiz_questions", schema = "app")
public class QuizQuestion {
    @EmbeddedId
    private QuizQuestionId id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public QuizQuestion() {
    }

    public QuizQuestion(QuizQuestionId id, String prompt, String explanation, boolean active) {
        this.id = id;
        this.prompt = prompt;
        this.explanation = explanation;
        this.active = active;
    }

    public QuizQuestionId getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getExplanation() {
        return explanation;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        this.active = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
