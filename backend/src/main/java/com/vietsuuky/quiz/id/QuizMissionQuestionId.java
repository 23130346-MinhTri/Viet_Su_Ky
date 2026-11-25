package com.vietsuuky.quiz.id;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class QuizMissionQuestionId implements Serializable {
    private UUID missionId;
    private Integer questionOrder;

    public QuizMissionQuestionId() {
    }

    public QuizMissionQuestionId(UUID missionId, Integer questionOrder) {
        this.missionId = missionId;
        this.questionOrder = questionOrder;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public void setMissionId(UUID missionId) {
        this.missionId = missionId;
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
        if (!(o instanceof QuizMissionQuestionId that)) return false;
        return Objects.equals(missionId, that.missionId)
                && Objects.equals(questionOrder, that.questionOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(missionId, questionOrder);
    }
}
