package com.aistudy.assistant.schedule.entity;

import com.aistudy.assistant.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Individual AI-proposed study session mapped to {@code study_plan_items}.
 */
@Entity
@Table(name = "study_plan_items")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private StudyPlan plan;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "planned_start", nullable = false)
    private Instant plannedStart;

    @Column(name = "planned_end", nullable = false)
    private Instant plannedEnd;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "decision", nullable = false, length = 20)
    private StudyPlanItemDecision decision = StudyPlanItemDecision.PENDING;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", unique = true)
    private Task task;

    @PrePersist
    protected void ensureDefaults() {
        if (this.decision == null) {
            this.decision = StudyPlanItemDecision.PENDING;
        }
    }
}
