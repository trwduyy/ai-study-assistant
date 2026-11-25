package com.aistudy.assistant.notification.entity;

/**
 * Processing lifecycle states for {@link NotificationOutbox} records.
 */
public enum NotificationOutboxStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED
}
