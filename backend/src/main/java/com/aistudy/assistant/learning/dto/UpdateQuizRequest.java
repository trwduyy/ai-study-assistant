package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.QuizDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateQuizRequest(
        @NotBlank(message = "Tiêu đề quiz không được để trống.")
        @Size(max = 200, message = "Tiêu đề quiz tối đa 200 ký tự.")
        String title,
        @NotNull(message = "Độ khó quiz không được để trống.")
        QuizDifficulty difficulty,
        @NotNull(message = "Phiên bản (version) không được để trống.")
        Integer version
) {
}
