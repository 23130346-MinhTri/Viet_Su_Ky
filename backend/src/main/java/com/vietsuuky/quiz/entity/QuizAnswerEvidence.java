package com.vietsuuky.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_answer_evidence", schema = "app")
public class QuizAnswerEvidence {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question_version", nullable = false)
    private Integer questionVersion;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Column(name = "selected_option_id", nullable = false)
    private UUID selectedOptionId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "request_hash", nullable = false, columnDefinition = "TEXT")
    private String requestHash;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    public QuizAnswerEvidence() {
    }

    public QuizAnswerEvidence(UUID id, UUID sessionId, UUID missionId, UUID questionId,
                              Integer questionVersion, Integer questionOrder, UUID selectedOptionId,
                              UUID eventId, String requestHash, String responseJson,
                              boolean correct, Integer points) {
        this.id = id;
        this.sessionId = sessionId;
        this.missionId = missionId;
        this.questionId = questionId;
        this.questionVersion = questionVersion;
        this.questionOrder = questionOrder;
        this.selectedOptionId = selectedOptionId;
        this.eventId = eventId;
        this.requestHash = requestHash;
        this.responseJson = responseJson;
        this.correct = correct;
        this.points = points;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getMissionId() { return missionId; }
    public UUID getQuestionId() { return questionId; }
    public Integer getQuestionVersion() { return questionVersion; }
    public Integer getQuestionOrder() { return questionOrder; }
    public UUID getSelectedOptionId() { return selectedOptionId; }
    public UUID getEventId() { return eventId; }
    public String getRequestHash() { return requestHash; }
    public String getResponseJson() { return responseJson; }
    public boolean isCorrect() { return correct; }
    public Integer getPoints() { return points; }
    public Instant getRecordedAt() { return recordedAt; }
}
