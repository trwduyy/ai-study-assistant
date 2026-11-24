package com.aistudy.assistant.dto.quiz;

import com.aistudy.assistant.entity.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateQuizRequest {

    @NotNull(message = "Course ID is required")
    private Long courseId;

    private Long materialId;

    @NotBlank(message = "Quiz title is required")
    @Size(min = 2, max = 200, message = "Quiz title must be between 2 and 200 characters")
    private String title;

    @NotNull(message = "Difficulty level is required (EASY, MEDIUM, HARD)")
    private Difficulty difficulty;

    @NotNull(message = "Question count is required")
    @Min(value = 1, message = "Question count must be at least 1")
    @Max(value = 30, message = "Question count cannot exceed 30")
    @Builder.Default
    private Integer questionCount = 5;
}
