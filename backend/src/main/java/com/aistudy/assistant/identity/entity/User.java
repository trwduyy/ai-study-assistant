package com.aistudy.assistant.identity.entity;

import com.aistudy.assistant.common.entity.BaseVersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;

/**
 * User account entity mapped to {@code users} with role, status, lockout tracking, and optimistic locking.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseVersionedEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.STUDENT;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Builder.Default
    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @PrePersist
    protected void ensureDefaults() {
        if (this.role == null) {
            this.role = UserRole.STUDENT;
        }
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
        if (this.failedLoginCount == null) {
            this.failedLoginCount = 0;
        }
    }

    public boolean isCurrentlyLocked(Instant now) {
        return this.status == UserStatus.LOCKED
                || (this.lockedUntil != null && this.lockedUntil.isAfter(now));
    }

    public void recordFailedLogin(Instant now, int maxFailedAttempts, Duration lockDuration) {
        int currentCount = this.failedLoginCount != null ? this.failedLoginCount : 0;
        this.failedLoginCount = currentCount + 1;
        if (this.failedLoginCount >= maxFailedAttempts) {
            this.lockedUntil = now.plus(lockDuration);
        }
    }

    public void resetFailedLogins() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }
}
