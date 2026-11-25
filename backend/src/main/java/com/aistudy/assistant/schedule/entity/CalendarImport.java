package com.aistudy.assistant.schedule.entity;

import com.aistudy.assistant.common.entity.BaseEntity;
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
 * Calendar import batch entity mapped to {@code calendar_imports} storing preview payload before confirmation.
 */
@Entity
@Table(name = "calendar_imports")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarImport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private CalendarImportSourceType sourceType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Ho_Chi_Minh";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private CalendarImportStatus status = CalendarImportStatus.PREVIEW;

    @Column(name = "preview_json", columnDefinition = "LONGTEXT")
    private String previewJson;

    @PrePersist
    protected void ensureDefaults() {
        if (this.timezone == null || this.timezone.isBlank()) {
            this.timezone = "Asia/Ho_Chi_Minh";
        }
        if (this.status == null) {
            this.status = CalendarImportStatus.PREVIEW;
        }
    }
}
