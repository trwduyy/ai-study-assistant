package com.aistudy.assistant.learning.flashcard.repository;

import com.aistudy.assistant.learning.flashcard.entity.FlashcardReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, Long> {

    Optional<FlashcardReview> findTopByCardIdAndUserIdOrderByIdDesc(Long cardId, Long userId);

    @Query("""
            SELECT r
            FROM FlashcardReview r
            JOIN FETCH r.card c
            WHERE c.deck.id = :deckId
              AND r.user.id = :userId
              AND r.id = (
                  SELECT MAX(r2.id)
                  FROM FlashcardReview r2
                  WHERE r2.card.id = c.id AND r2.user.id = :userId
              )
            """)
    List<FlashcardReview> findLatestReviewsByDeckAndUser(
            @Param("deckId") Long deckId,
            @Param("userId") Long userId
    );
}
