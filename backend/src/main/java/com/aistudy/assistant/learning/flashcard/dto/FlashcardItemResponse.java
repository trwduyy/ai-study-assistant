package com.aistudy.assistant.learning.flashcard.dto;

import com.aistudy.assistant.learning.flashcard.entity.FlashcardRating;

import java.time.Instant;

public record FlashcardItemResponse(
        Long id,
        Long deckId,
        String front,
        String back,
        Long sourceChunkId,
        FlashcardRating lastRating,
        double easeFactor,
        int intervalDays,
        int repetitions,
        Instant nextReviewAt,
        Instant createdAt,
        Instant updatedAt
) {
}
