package com.aistudy.assistant.learning.flashcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFlashcardDeckRequest {

    private Long courseId;

    @NotBlank(message = "Tên bộ flashcard không được để trống.")
    @Size(max = 255, message = "Tên bộ flashcard tối đa 255 ký tự.")
    private String title;
}
