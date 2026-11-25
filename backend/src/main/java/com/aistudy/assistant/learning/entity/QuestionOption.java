package com.aistudy.assistant.learning.entity;

import com.aistudy.assistant.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Multiple-choice answer option entity mapped to {@code question_options}.
 */
@Entity
@Table(name = "question_options")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "label", nullable = false, length = 10)
    private String label;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = Boolean.FALSE;

    @PrePersist
    protected void ensureDefaults() {
        if (this.isCorrect == null) {
            this.isCorrect = Boolean.FALSE;
        }
    }

    public boolean isCorrectOption() {
        return Boolean.TRUE.equals(this.isCorrect);
    }
}
