package com.aistudy.assistant.learning.flashcard.dto;

import com.aistudy.assistant.learning.flashcard.entity.FlashcardRating;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitFlashcardReviewRequest {

    @NotNull(message = "Mức độ ghi nhớ (rating) là bắt buộc.")
    private FlashcardRating rating;
}
