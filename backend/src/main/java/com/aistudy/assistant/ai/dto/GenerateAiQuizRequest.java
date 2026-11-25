package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.learning.entity.QuestionType;
import com.aistudy.assistant.learning.entity.QuizDifficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GenerateAiQuizRequest(
        @NotNull(message = "courseId không được để trống.")
        Long courseId,
        @NotEmpty(message = "Vui lòng chọn ít nhất một tài liệu nguồn.")
        List<Long> materialIds,
        Long topicId,
        @NotBlank(message = "Tiêu đề quiz không được để trống.")
        @Size(max = 200, message = "Tiêu đề quiz tối đa 200 ký tự.")
        String title,
        @Size(max = 200, message = "Chủ đề tối đa 200 ký tự.")
        String topic,
        @Min(value = 1, message = "Số câu hỏi tối thiểu là 1.")
        @Max(value = 50, message = "Số câu hỏi tối đa là 50.")
        Integer questionCount,
        QuizDifficulty difficulty,
        QuestionType type
) {
}
