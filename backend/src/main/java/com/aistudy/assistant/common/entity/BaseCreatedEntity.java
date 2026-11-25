package com.aistudy.assistant.common.entity;

import jakarta.persistence.MappedSuperclass;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Legacy compatibility superclass preserved during Phase 0; V2 entities should extend {@link BaseEntity} directly.
 */
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public abstract class BaseCreatedEntity extends BaseEntity {
}
