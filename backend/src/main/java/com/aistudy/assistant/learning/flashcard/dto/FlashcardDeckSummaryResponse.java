package com.aistudy.assistant.learning.flashcard.dto;

import java.time.Instant;

public record FlashcardDeckSummaryResponse(
        Long id,
        Long courseId,
        String courseName,
        String title,
        long totalCards,
        long dueCardsCount,
        Instant createdAt,
        Instant updatedAt
) {
}
