package com.aistudy.assistant.schedule.entity;

import com.aistudy.assistant.classroom.entity.Classroom;
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
 * Calendar/meeting event entity mapped to {@code events} storing UTC timestamps, source timezone, and optimistic locking.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Event extends BaseVersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id")
    private CalendarImport calendarImport;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private EventType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Ho_Chi_Minh";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status = EventStatus.UPCOMING;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "source", nullable = false, length = 20)
    private EventSource source = EventSource.MANUAL;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @PrePersist
    protected void ensureDefaults() {
        if (this.timezone == null || this.timezone.isBlank()) {
            this.timezone = "Asia/Ho_Chi_Minh";
        }
        if (this.status == null) {
            this.status = EventStatus.UPCOMING;
        }
        if (this.source == null) {
            this.source = EventSource.MANUAL;
        }
    }
}
