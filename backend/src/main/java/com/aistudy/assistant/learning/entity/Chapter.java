package com.aistudy.assistant.learning.entity;

import com.aistudy.assistant.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Chapter entity belonging to a {@link Course}, ordered by {@code position}.
 */
@Entity
@Table(name = "chapters")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Chapter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    @Builder.Default
    @Column(nullable = false)
    private Integer position = 0;

    @Builder.Default
    @OneToMany(mappedBy = "chapter", fetch = FetchType.LAZY)
    @OrderBy("position ASC, id ASC")
    private List<Topic> topics = new ArrayList<>();

    @PrePersist
    protected void ensureDefaults() {
        if (this.position == null) {
            this.position = 0;
        }
    }
}
