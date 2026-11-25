package com.aistudy.assistant.schedule.entity;

import com.aistudy.assistant.ai.entity.AiJob;
import com.aistudy.assistant.common.entity.BaseEntity;
import com.aistudy.assistant.identity.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * AI-generated study plan proposal mapped to {@code study_plans}.
 */
@Entity
@Table(name = "study_plans")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private AiJob job;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private StudyPlanStatus status = StudyPlanStatus.PROPOSED;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    @Builder.Default
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StudyPlanItem> items = new ArrayList<>();

    @PrePersist
    protected void ensureDefaults() {
        if (this.status == null) {
            this.status = StudyPlanStatus.PROPOSED;
        }
    }
}
