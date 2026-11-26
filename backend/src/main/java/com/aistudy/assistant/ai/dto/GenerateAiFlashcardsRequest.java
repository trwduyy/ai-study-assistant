package com.aistudy.assistant.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GenerateAiFlashcardsRequest(
        @NotEmpty(message = "Danh sách tài liệu nguồn không được để trống.")
        List<Long> materialIds,
        Long deckId,
        @Size(max = 255, message = "Tên bộ flashcard tối đa 255 ký tự.")
        String deckTitle,
        Long courseId,
        @Min(value = 1, message = "Số lượng thẻ tối thiểu là 1.")
        @Max(value = 30, message = "Số lượng thẻ tối đa là 30.")
        Integer cardCount
) {
}
