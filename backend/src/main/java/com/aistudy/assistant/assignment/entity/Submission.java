package com.aistudy.assistant.assignment.entity;

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
 * Student assignment submission entity mapped to {@code submissions} enforcing single-submission
 * uniqueness per student-assignment pair and optimistic locking.
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Submission extends BaseVersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "text_answer", columnDefinition = "LONGTEXT")
    private String textAnswer;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Builder.Default
    @Column(name = "is_late", nullable = false)
    private Boolean isLate = Boolean.FALSE;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "score", precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @PrePersist
    protected void ensureDefaults() {
        if (this.status == null) {
            this.status = SubmissionStatus.SUBMITTED;
        }
        if (this.isLate == null) {
            this.isLate = Boolean.FALSE;
        }
        if (this.submittedAt == null) {
            this.submittedAt = Instant.now();
        }
    }

    public boolean isReopenedForResubmission() {
        return this.status == SubmissionStatus.RETURNED
                && this.gradedAt == null
                && this.score == null;
    }
}
