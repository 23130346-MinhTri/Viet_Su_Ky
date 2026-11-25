package com.vietsuuky.quiz.id;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class QuizQuestionId implements Serializable {
    private UUID id;
    private Integer version;

    public QuizQuestionId() {
    }

    public QuizQuestionId(UUID id, Integer version) {
        this.id = id;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuizQuestionId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
