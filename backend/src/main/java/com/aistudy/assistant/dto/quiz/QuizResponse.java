package com.aistudy.assistant.dto.quiz;

import com.aistudy.assistant.entity.Difficulty;
import com.aistudy.assistant.entity.Quiz;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {

    private Long id;
    private String title;
    private Difficulty difficulty;
    private Integer questionCount;
    private Long courseId;
    private Long materialId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    public static QuizResponse fromEntity(Quiz quiz) {
        if (quiz == null) {
            return null;
        }

        return QuizResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .difficulty(quiz.getDifficulty())
                .questionCount(quiz.getQuestionCount())
                .courseId(quiz.getCourse() != null ? quiz.getCourse().getId() : null)
                .materialId(quiz.getMaterial() != null ? quiz.getMaterial().getId() : null)
                .createdAt(quiz.getCreatedAt())
                .build();
    }
}
