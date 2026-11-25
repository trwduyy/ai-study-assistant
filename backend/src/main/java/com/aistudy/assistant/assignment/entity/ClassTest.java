package com.aistudy.assistant.assignment.entity;

import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.common.entity.BaseEntity;
import com.aistudy.assistant.learning.entity.Quiz;
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

import java.time.Instant;

/**
 * Scheduled timed classroom test mapped to {@code class_tests} referencing a {@link Quiz}.
 */
@Entity
@Table(name = "class_tests")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClassTest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 1;

    @Builder.Default
    @Column(name = "shuffle_questions", nullable = false)
    private Boolean shuffleQuestions = Boolean.FALSE;

    @PrePersist
    protected void ensureDefaults() {
        if (this.maxAttempts == null) {
            this.maxAttempts = 1;
        }
        if (this.shuffleQuestions == null) {
            this.shuffleQuestions = Boolean.FALSE;
        }
    }
}
