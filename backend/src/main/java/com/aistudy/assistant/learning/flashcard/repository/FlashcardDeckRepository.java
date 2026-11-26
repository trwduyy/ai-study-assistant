package com.aistudy.assistant.learning.flashcard.repository;

import com.aistudy.assistant.learning.flashcard.dto.FlashcardDeckSummaryResponse;
import com.aistudy.assistant.learning.flashcard.entity.FlashcardDeck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeck, Long> {

    @EntityGraph(attributePaths = {"owner", "course"})
    Optional<FlashcardDeck> findWithDetailsById(Long id);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.learning.flashcard.dto.FlashcardDeckSummaryResponse(
                        d.id,
                        c.id,
                        c.name,
                        d.title,
                        (SELECT COUNT(f) FROM Flashcard f WHERE f.deck.id = d.id),
                        (SELECT COUNT(f2) FROM Flashcard f2
                         WHERE f2.deck.id = d.id
                           AND (
                               NOT EXISTS (SELECT 1 FROM FlashcardReview r1
                                           WHERE r1.card.id = f2.id AND r1.user.id = :ownerId)
                               OR EXISTS (
                                   SELECT 1 FROM FlashcardReview r2
                                   WHERE r2.card.id = f2.id AND r2.user.id = :ownerId
                                     AND r2.id = (SELECT MAX(r3.id) FROM FlashcardReview r3
                                                  WHERE r3.card.id = f2.id AND r3.user.id = :ownerId)
                                     AND r2.nextReviewAt <= :now
                               )
                           )),
                        d.createdAt,
                        d.updatedAt
                    )
                    FROM FlashcardDeck d
                    LEFT JOIN d.course c
                    WHERE d.owner.id = :ownerId
                      AND (:courseId IS NULL OR c.id = :courseId)
                    """,
            countQuery = """
                    SELECT COUNT(d)
                    FROM FlashcardDeck d
                    WHERE d.owner.id = :ownerId
                      AND (:courseId IS NULL OR d.course.id = :courseId)
                    """
    )
    Page<FlashcardDeckSummaryResponse> findProjectedByOwnerId(
            @Param("ownerId") Long ownerId,
            @Param("courseId") Long courseId,
            @Param("now") Instant now,
            Pageable pageable
    );
}
