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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Per-question selected option snapshot mapped to {@code attempt_answers}.
 */
@Entity
@Table(name = "attempt_answers")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_option_ids", columnDefinition = "json")
    private List<Long> selectedOptionIds;

    @Builder.Default
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = Boolean.FALSE;

    @PrePersist
    protected void ensureDefaults() {
        if (this.isCorrect == null) {
            this.isCorrect = Boolean.FALSE;
        }
    }
}
