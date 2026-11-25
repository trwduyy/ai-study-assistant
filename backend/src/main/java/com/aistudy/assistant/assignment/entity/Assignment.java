package com.aistudy.assistant.assignment.entity;

import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.common.entity.BaseVersionedEntity;
import com.aistudy.assistant.identity.entity.User;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Classroom assignment entity mapped to {@code assignments} with UTC deadlines and optimistic locking.
 */
@Entity
@Table(name = "assignments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Assignment extends BaseVersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Builder.Default
    @Column(name = "max_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxScore = new BigDecimal("10.00");

    @Column(name = "open_at")
    private Instant openAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Builder.Default
    @Column(name = "allow_late", nullable = false)
    private Boolean allowLate = Boolean.FALSE;

    @Builder.Default
    @Column(name = "max_submissions", nullable = false)
    private Integer maxSubmissions = 1;

    @PrePersist
    protected void ensureDefaults() {
        if (this.maxScore == null) {
            this.maxScore = new BigDecimal("10.00");
        }
        if (this.allowLate == null) {
            this.allowLate = Boolean.FALSE;
        }
        if (this.maxSubmissions == null) {
            this.maxSubmissions = 1;
        }
    }
}
