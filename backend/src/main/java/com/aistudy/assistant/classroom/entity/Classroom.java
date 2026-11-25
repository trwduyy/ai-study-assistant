package com.aistudy.assistant.classroom.entity;

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

import java.time.Instant;

/**
 * Classroom entity mapped to {@code classrooms} with unique join code and optimistic locking.
 */
@Entity
@Table(name = "classrooms")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Classroom extends BaseVersionedEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "subject", length = 150)
    private String subject;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private ClassroomStatus status = ClassroomStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @PrePersist
    protected void ensureDefaults() {
        if (this.status == null) {
            this.status = ClassroomStatus.OPEN;
        }
    }
}
