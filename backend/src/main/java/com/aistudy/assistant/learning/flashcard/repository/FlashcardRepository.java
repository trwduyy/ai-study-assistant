package com.aistudy.assistant.learning.flashcard.repository;

import com.aistudy.assistant.learning.flashcard.entity.Flashcard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    @EntityGraph(attributePaths = {"deck", "deck.owner", "sourceChunk"})
    Optional<Flashcard> findWithDeckAndOwnerById(Long id);

    @EntityGraph(attributePaths = {"sourceChunk"})
    List<Flashcard> findByDeckIdOrderByIdAsc(Long deckId);

    @EntityGraph(attributePaths = {"sourceChunk"})
    @Query("""
            SELECT f
            FROM Flashcard f
            WHERE f.deck.id = :deckId
              AND (
                  NOT EXISTS (SELECT 1 FROM FlashcardReview r1
                              WHERE r1.card.id = f.id AND r1.user.id = :userId)
                  OR EXISTS (
                      SELECT 1 FROM FlashcardReview r2
                      WHERE r2.card.id = f.id AND r2.user.id = :userId
                        AND r2.id = (SELECT MAX(r3.id) FROM FlashcardReview r3
                                     WHERE r3.card.id = f.id AND r3.user.id = :userId)
                        AND r2.nextReviewAt <= :now
                  )
              )
            ORDER BY f.id ASC
            """)
    List<Flashcard> findDueCardsForReview(
            @Param("deckId") Long deckId,
            @Param("userId") Long userId,
            @Param("now") Instant now
    );

    @Query("""
            SELECT COUNT(f)
            FROM Flashcard f
            WHERE f.deck.owner.id = :userId
              AND (
                  NOT EXISTS (SELECT 1 FROM FlashcardReview r1
                              WHERE r1.card.id = f.id AND r1.user.id = :userId)
                  OR EXISTS (
                      SELECT 1 FROM FlashcardReview r2
                      WHERE r2.card.id = f.id AND r2.user.id = :userId
                        AND r2.id = (SELECT MAX(r3.id) FROM FlashcardReview r3
                                     WHERE r3.card.id = f.id AND r3.user.id = :userId)
                        AND r2.nextReviewAt <= :now
                  )
              )
            """)
    long countDueCardsByUserId(
            @Param("userId") Long userId,
            @Param("now") Instant now
    );
}
