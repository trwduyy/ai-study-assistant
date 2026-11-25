package com.aistudy.assistant.learning.flashcard.dto;

import java.util.List;

public record FlashcardReviewQueueResponse(
        Long deckId,
        String deckTitle,
        int dueCount,
        List<FlashcardItemResponse> cards
) {
}
