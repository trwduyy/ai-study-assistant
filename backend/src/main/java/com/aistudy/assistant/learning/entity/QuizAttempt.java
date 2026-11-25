package com.aistudy.assistant.learning.entity;

import com.aistudy.assistant.common.entity.BaseVersionedEntity;
import com.aistudy.assistant.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Quiz attempt entity mapped to {@code quiz_attempts} with optimistic locking and submission idempotency key.
 */
@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttempt extends BaseVersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "test_id")
    private Long testId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Builder.Default
    @Column(name = "score", nullable = false, precision = 6, scale = 2)
    private BigDecimal score = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "max_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxScore = new BigDecimal("10.00");

    @Builder.Default
    @Column(name = "correct_count", nullable = false)
    private Integer correctCount = 0;

    @Builder.Default
    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions = 0;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @PrePersist
    protected void ensureDefaults() {
        if (this.status == null) {
            this.status = AttemptStatus.IN_PROGRESS;
        }
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        if (this.score == null) {
            this.score = BigDecimal.ZERO;
        }
        if (this.maxScore == null) {
            this.maxScore = new BigDecimal("10.00");
        }
        if (this.correctCount == null) {
            this.correctCount = 0;
        }
        if (this.totalQuestions == null) {
            this.totalQuestions = 0;
        }
    }
}
