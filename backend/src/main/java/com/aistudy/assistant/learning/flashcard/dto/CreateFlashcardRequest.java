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
public class CreateFlashcardRequest {

    @NotBlank(message = "Mặt trước của thẻ không được để trống.")
    @Size(max = 5000, message = "Nội dung mặt trước tối đa 5000 ký tự.")
    private String front;

    @NotBlank(message = "Mặt sau của thẻ không được để trống.")
    @Size(max = 5000, message = "Nội dung mặt sau tối đa 5000 ký tự.")
    private String back;

    private Long sourceChunkId;
}
