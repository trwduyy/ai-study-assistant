package com.aistudy.assistant.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Base mapped superclass combining UTC audit timestamps, soft deletion, and optimistic locking.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseFullEntity extends BaseSoftDeleteEntity {

    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @PrePersist
    protected void ensureInitialVersion() {
        if (this.version == null) {
            this.version = 0;
        }
    }
}
