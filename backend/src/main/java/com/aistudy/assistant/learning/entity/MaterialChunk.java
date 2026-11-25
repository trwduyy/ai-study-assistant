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
 * Page-aware extracted text chunk belonging to a {@link Material}.
 */
@Entity
@Table(name = "material_chunks")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialChunk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "page_from")
    private Integer pageFrom;

    @Column(name = "page_to")
    private Integer pageTo;

    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Builder.Default
    @Column(name = "token_count", nullable = false)
    private Integer tokenCount = 0;

    @PrePersist
    protected void ensureDefaults() {
        if (this.tokenCount == null) {
            this.tokenCount = 0;
        }
    }
}
