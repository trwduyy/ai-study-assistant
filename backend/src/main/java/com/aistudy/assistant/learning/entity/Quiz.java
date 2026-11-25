package com.aistudy.assistant.learning.entity;

import com.aistudy.assistant.common.entity.BaseFullEntity;
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

/**
 * Practice quiz entity mapped to {@code quizzes} supporting soft deletion and optimistic locking.
 */
@Entity
@Table(name = "quizzes")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Quiz extends BaseFullEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "difficulty", nullable = false, length = 20)
    private QuizDifficulty difficulty = QuizDifficulty.MEDIUM;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "source_job_id", length = 36, columnDefinition = "CHAR(36)")
    private String sourceJobId;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @PrePersist
    protected void ensureDefaults() {
        if (this.difficulty == null) {
            this.difficulty = QuizDifficulty.MEDIUM;
        }
        if (this.questionCount == null) {
            this.questionCount = 0;
        }
    }
}
