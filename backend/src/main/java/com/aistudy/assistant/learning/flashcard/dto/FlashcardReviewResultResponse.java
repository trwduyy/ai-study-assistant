package com.aistudy.assistant.learning.flashcard.dto;

import com.aistudy.assistant.learning.flashcard.entity.FlashcardRating;

import java.time.Instant;

public record FlashcardReviewResultResponse(
        Long reviewId,
        Long cardId,
        FlashcardRating rating,
        double easeFactor,
        int intervalDays,
        int repetitions,
        Instant nextReviewAt,
        Instant reviewedAt
) {
}
