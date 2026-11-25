package com.aistudy.assistant.learning.entity;

import com.aistudy.assistant.common.entity.BaseEntity;
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
 * Topic entity belonging to a {@link Chapter}, ordered by {@code position}.
 */
@Entity
@Table(name = "topics")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Topic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(nullable = false, length = 200)
    private String title;

    @Builder.Default
    @Column(nullable = false)
    private Integer position = 0;

    @PrePersist
    protected void ensureDefaults() {
        if (this.position == null) {
            this.position = 0;
        }
    }
}
