package com.aistudy.assistant.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@code quiz_materials}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class QuizMaterialId implements Serializable {

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;
}
