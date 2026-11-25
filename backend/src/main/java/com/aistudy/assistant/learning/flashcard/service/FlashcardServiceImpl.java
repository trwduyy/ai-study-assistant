package com.aistudy.assistant.learning.flashcard.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.MaterialChunk;
import com.aistudy.assistant.learning.flashcard.dto.CreateFlashcardDeckRequest;
import com.aistudy.assistant.learning.flashcard.dto.CreateFlashcardRequest;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardDeckDetailResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardDeckSummaryResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardItemResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardReviewQueueResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardReviewResultResponse;
import com.aistudy.assistant.learning.flashcard.dto.SubmitFlashcardReviewRequest;
import com.aistudy.assistant.learning.flashcard.dto.UpdateFlashcardDeckRequest;
import com.aistudy.assistant.learning.flashcard.entity.Flashcard;
import com.aistudy.assistant.learning.flashcard.entity.FlashcardDeck;
import com.aistudy.assistant.learning.flashcard.entity.FlashcardReview;
import com.aistudy.assistant.learning.flashcard.repository.FlashcardDeckRepository;
import com.aistudy.assistant.learning.flashcard.repository.FlashcardRepository;
import com.aistudy.assistant.learning.flashcard.repository.FlashcardReviewRepository;
import com.aistudy.assistant.learning.flashcard.service.SpacedRepetitionCalculator.Sm2Schedule;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business service for flashcard decks, manual card creation, and SM-2 spaced repetition reviews (FR-08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashcardServiceImpl implements FlashcardService {

    private final FlashcardDeckRepository deckRepository;
    private final FlashcardRepository flashcardRepository;
    private final FlashcardReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final UserRepository userRepository;
    private final SpacedRepetitionCalculator sm2Calculator;

    @Override
    @Transactional(readOnly = true)
    public Page<FlashcardDeckSummaryResponse> listDecks(Long courseId, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        return deckRepository.findProjectedByOwnerId(currentUserId, courseId, Instant.now(), pageable);
    }

    @Override
    @Transactional
    public FlashcardDeckSummaryResponse createDeck(CreateFlashcardDeckRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Course course = resolveOwnedCourse(request.getCourseId(), currentUserId);
        User owner = userRepository.getReferenceById(currentUserId);

        FlashcardDeck deck = FlashcardDeck.builder()
                .owner(owner)
                .course(course)
                .title(request.getTitle().trim())
                .build();
        FlashcardDeck saved = deckRepository.save(deck);

        log.info("Created flashcard deck id={} by userId={}", saved.getId(), currentUserId);
        return toDeckSummary(saved, 0L, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public FlashcardDeckDetailResponse getDeckDetail(Long deckId) {
        Long currentUserId = SecurityUtils.currentUserId();
        FlashcardDeck deck = requireOwnedDeck(deckId, currentUserId);

        List<Flashcard> cards = flashcardRepository.findByDeckIdOrderByIdAsc(deckId);
        Map<Long, FlashcardReview> latestReviewsByCardId = loadLatestReviewsMap(deckId, currentUserId);

        Instant now = Instant.now();
        long dueCount = 0L;
        List<FlashcardItemResponse> cardDtos = new java.util.ArrayList<>(cards.size());
        for (Flashcard card : cards) {
            FlashcardReview review = latestReviewsByCardId.get(card.getId());
            if (review == null || !review.getNextReviewAt().isAfter(now)) {
                dueCount++;
            }
            cardDtos.add(toCardItemResponse(card, review));
        }

        return new FlashcardDeckDetailResponse(
                deck.getId(),
                deck.getCourse() != null ? deck.getCourse().getId() : null,
                deck.getCourse() != null ? deck.getCourse().getName() : null,
                deck.getTitle(),
                cards.size(),
                dueCount,
                cardDtos,
                deck.getCreatedAt(),
                deck.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public FlashcardDeckSummaryResponse updateDeck(Long deckId, UpdateFlashcardDeckRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        FlashcardDeck deck = requireOwnedDeck(deckId, currentUserId);
        Course course = resolveOwnedCourse(request.getCourseId(), currentUserId);

        deck.setTitle(request.getTitle().trim());
        deck.setCourse(course);
        FlashcardDeck saved = deckRepository.save(deck);

        List<Flashcard> cards = flashcardRepository.findByDeckIdOrderByIdAsc(deckId);
        Map<Long, FlashcardReview> latestReviews = loadLatestReviewsMap(deckId, currentUserId);
        Instant now = Instant.now();
        long dueCount = cards.stream()
                .filter(card -> {
                    FlashcardReview r = latestReviews.get(card.getId());
                    return r == null || !r.getNextReviewAt().isAfter(now);
                })
                .count();

        return toDeckSummary(saved, cards.size(), dueCount);
    }

    @Override
    @Transactional
    public void deleteDeck(Long deckId) {
        Long currentUserId = SecurityUtils.currentUserId();
        FlashcardDeck deck = requireOwnedDeck(deckId, currentUserId);
        deckRepository.delete(deck);
        log.info("Deleted flashcard deck id={} by userId={}", deckId, currentUserId);
    }

    @Override
    @Transactional
    public FlashcardItemResponse addCard(Long deckId, CreateFlashcardRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        FlashcardDeck deck = requireOwnedDeck(deckId, currentUserId);

        MaterialChunk sourceChunk = null;
        if (request.getSourceChunkId() != null) {
            sourceChunk = materialChunkRepository.findById(request.getSourceChunkId())
                    .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_NOT_FOUND, "Không tìm thấy đoạn tài liệu nguồn."));
            if (!sourceChunk.getMaterial().getOwner().getId().equals(currentUserId)) {
                throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
            }
        }

        Flashcard card = Flashcard.builder()
                .deck(deck)
                .front(request.getFront().trim())
                .back(request.getBack().trim())
                .sourceChunk(sourceChunk)
                .build();
        Flashcard saved = flashcardRepository.save(card);

        log.info("Added flashcard id={} to deckId={} by userId={}", saved.getId(), deckId, currentUserId);
        return toCardItemResponse(saved, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FlashcardReviewQueueResponse getDueCards(Long deckId) {
        Long currentUserId = SecurityUtils.currentUserId();
        FlashcardDeck deck = requireOwnedDeck(deckId, currentUserId);

        Instant now = Instant.now();
        List<Flashcard> dueCards = flashcardRepository.findDueCardsForReview(deckId, currentUserId, now);
        Map<Long, FlashcardReview> latestReviews = dueCards.isEmpty()
                ? Map.of()
                : loadLatestReviewsMap(deckId, currentUserId);

        List<FlashcardItemResponse> items = dueCards.stream()
                .map(card -> toCardItemResponse(card, latestReviews.get(card.getId())))
                .toList();

        return new FlashcardReviewQueueResponse(deck.getId(), deck.getTitle(), items.size(), items);
    }

    @Override
    @Transactional
    public FlashcardReviewResultResponse submitReview(Long cardId, SubmitFlashcardReviewRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Flashcard card = flashcardRepository.findWithDeckAndOwnerById(cardId)
                .orElseThrow(() -> new AppException(ErrorCode.FLASHCARD_NOT_FOUND));
        if (!card.getDeck().getOwner().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        FlashcardReview previous = reviewRepository
                .findTopByCardIdAndUserIdOrderByIdDesc(cardId, currentUserId)
                .orElse(null);

        Instant now = Instant.now();
        Sm2Schedule nextSchedule = sm2Calculator.calculate(previous, request.getRating(), now);

        FlashcardReview review = FlashcardReview.builder()
                .card(card)
                .user(userRepository.getReferenceById(currentUserId))
                .rating(request.getRating())
                .easeFactor(nextSchedule.easeFactor())
                .intervalDays(nextSchedule.intervalDays())
                .repetitions(nextSchedule.repetitions())
                .nextReviewAt(nextSchedule.nextReviewAt())
                .build();
        FlashcardReview saved = reviewRepository.save(review);

        return new FlashcardReviewResultResponse(
                saved.getId(),
                card.getId(),
                saved.getRating(),
                saved.getEaseFactor(),
                saved.getIntervalDays(),
                saved.getRepetitions(),
                saved.getNextReviewAt(),
                saved.getCreatedAt()
        );
    }

    private FlashcardDeck requireOwnedDeck(Long deckId, Long userId) {
        FlashcardDeck deck = deckRepository.findWithDetailsById(deckId)
                .orElseThrow(() -> new AppException(ErrorCode.FLASHCARD_DECK_NOT_FOUND));
        if (!deck.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return deck;
    }

    private Course resolveOwnedCourse(Long courseId, Long userId) {
        if (courseId == null) {
            return null;
        }
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return course;
    }

    private Map<Long, FlashcardReview> loadLatestReviewsMap(Long deckId, Long userId) {
        return reviewRepository.findLatestReviewsByDeckAndUser(deckId, userId).stream()
                .collect(Collectors.toMap(r -> r.getCard().getId(), Function.identity(), (first, ignored) -> first));
    }

    private FlashcardDeckSummaryResponse toDeckSummary(FlashcardDeck deck, long totalCards, long dueCardsCount) {
        return new FlashcardDeckSummaryResponse(
                deck.getId(),
                deck.getCourse() != null ? deck.getCourse().getId() : null,
                deck.getCourse() != null ? deck.getCourse().getName() : null,
                deck.getTitle(),
                totalCards,
                dueCardsCount,
                deck.getCreatedAt(),
                deck.getUpdatedAt()
        );
    }

    private FlashcardItemResponse toCardItemResponse(Flashcard card, FlashcardReview latestReview) {
        return new FlashcardItemResponse(
                card.getId(),
                card.getDeck().getId(),
                card.getFront(),
                card.getBack(),
                card.getSourceChunk() != null ? card.getSourceChunk().getId() : null,
                latestReview != null ? latestReview.getRating() : null,
                latestReview != null ? latestReview.getEaseFactor() : SpacedRepetitionCalculator.DEFAULT_EASE_FACTOR,
                latestReview != null ? latestReview.getIntervalDays() : 0,
                latestReview != null ? latestReview.getRepetitions() : 0,
                latestReview != null ? latestReview.getNextReviewAt() : null,
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
