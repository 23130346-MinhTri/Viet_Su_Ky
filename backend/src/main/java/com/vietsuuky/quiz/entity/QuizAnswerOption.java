package com.vietsuuky.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_answer_options", schema = "app")
public class QuizAnswerOption {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question_version", nullable = false)
    private Integer questionVersion;

    @Column(name = "option_key", nullable = false, length = 20)
    private String optionKey;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public QuizAnswerOption() {
    }

    public QuizAnswerOption(UUID id, UUID questionId, Integer questionVersion, String optionKey,
                            String optionText, Integer displayOrder, boolean correct) {
        this.id = id;
        this.questionId = questionId;
        this.questionVersion = questionVersion;
        this.optionKey = optionKey;
        this.optionText = optionText;
        this.displayOrder = displayOrder;
        this.correct = correct;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public Integer getQuestionVersion() {
        return questionVersion;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public String getOptionText() {
        return optionText;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public boolean isCorrect() {
        return correct;
    }
}
