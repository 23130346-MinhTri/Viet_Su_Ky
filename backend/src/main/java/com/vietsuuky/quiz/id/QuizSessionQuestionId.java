package com.vietsuuky.quiz.id;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class QuizSessionQuestionId implements Serializable {
    private UUID sessionId;
    private Integer questionOrder;

    public QuizSessionQuestionId() {
    }

    public QuizSessionQuestionId(UUID sessionId, Integer questionOrder) {
        this.sessionId = sessionId;
        this.questionOrder = questionOrder;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getQuestionOrder() {
        return questionOrder;
    }

    public void setQuestionOrder(Integer questionOrder) {
        this.questionOrder = questionOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuizSessionQuestionId that)) return false;
        return Objects.equals(sessionId, that.sessionId)
                && Objects.equals(questionOrder, that.questionOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, questionOrder);
    }
}
