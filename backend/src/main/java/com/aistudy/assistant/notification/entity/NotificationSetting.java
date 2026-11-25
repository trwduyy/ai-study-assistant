package com.aistudy.assistant.notification.entity;

import com.aistudy.assistant.common.entity.BaseEntity;
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

/**
 * Per-user delivery channel preferences by notification event type mapped to {@code notification_settings}.
 */
@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSetting extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Builder.Default
    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled = Boolean.TRUE;

    @Builder.Default
    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = Boolean.TRUE;

    @PrePersist
    protected void ensureDefaults() {
        if (this.inAppEnabled == null) {
            this.inAppEnabled = Boolean.TRUE;
        }
        if (this.emailEnabled == null) {
            this.emailEnabled = Boolean.TRUE;
        }
    }
}
