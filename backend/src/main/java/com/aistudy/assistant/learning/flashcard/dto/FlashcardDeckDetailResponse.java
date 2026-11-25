package com.aistudy.assistant.learning.flashcard.dto;

import java.time.Instant;
import java.util.List;

public record FlashcardDeckDetailResponse(
        Long id,
        Long courseId,
        String courseName,
        String title,
        long totalCards,
        long dueCardsCount,
        List<FlashcardItemResponse> cards,
        Instant createdAt,
        Instant updatedAt
) {
}
