package com.aistudy.assistant.learning.flashcard.service;

import com.aistudy.assistant.learning.flashcard.dto.CreateFlashcardDeckRequest;
import com.aistudy.assistant.learning.flashcard.dto.CreateFlashcardRequest;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardDeckDetailResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardDeckSummaryResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardItemResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardReviewQueueResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardReviewResultResponse;
import com.aistudy.assistant.learning.flashcard.dto.SubmitFlashcardReviewRequest;
import com.aistudy.assistant.learning.flashcard.dto.UpdateFlashcardDeckRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlashcardService {

    Page<FlashcardDeckSummaryResponse> listDecks(Long courseId, Pageable pageable);

    FlashcardDeckSummaryResponse createDeck(CreateFlashcardDeckRequest request);

    FlashcardDeckDetailResponse getDeckDetail(Long deckId);

    FlashcardDeckSummaryResponse updateDeck(Long deckId, UpdateFlashcardDeckRequest request);

    void deleteDeck(Long deckId);

    FlashcardItemResponse addCard(Long deckId, CreateFlashcardRequest request);

    FlashcardReviewQueueResponse getDueCards(Long deckId);

    FlashcardReviewResultResponse submitReview(Long cardId, SubmitFlashcardReviewRequest request);
}
