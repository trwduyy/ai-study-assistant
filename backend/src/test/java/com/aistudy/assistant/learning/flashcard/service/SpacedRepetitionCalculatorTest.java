package com.aistudy.assistant.learning.flashcard.service;

import com.aistudy.assistant.learning.flashcard.entity.FlashcardRating;
import com.aistudy.assistant.learning.flashcard.entity.FlashcardReview;
import com.aistudy.assistant.learning.flashcard.service.SpacedRepetitionCalculator.Sm2Schedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpacedRepetitionCalculatorTest {

    private final SpacedRepetitionCalculator calculator = new SpacedRepetitionCalculator();

    @Test
    @DisplayName("FORGOT resets repetitions to 0, reduces easeFactor, and schedules 10-minute re-review")
    void calculate_Forgot_ResetsRepetitionsAndRequeuesShortly() {
        Instant now = Instant.parse("2026-11-25T14:00:00Z");
        FlashcardReview previous = FlashcardReview.builder()
                .easeFactor(2.5)
                .repetitions(3)
                .intervalDays(12)
                .build();

        Sm2Schedule next = calculator.calculate(previous, FlashcardRating.FORGOT, now);

        assertEquals(0, next.repetitions());
        assertEquals(0, next.intervalDays());
        assertEquals(2.3, next.easeFactor());
        assertEquals(now.plus(Duration.ofMinutes(10)), next.nextReviewAt());
    }

    @Test
    @DisplayName("HARD progresses intervals conservatively and lowers easeFactor with 1.3 floor")
    void calculate_Hard_ProgressesConservativelyAndClampsEaseFactor() {
        Instant now = Instant.parse("2026-11-25T14:00:00Z");

        Sm2Schedule first = calculator.calculate(null, FlashcardRating.HARD, now);
        assertEquals(1, first.repetitions());
        assertEquals(1, first.intervalDays());
        assertEquals(2.36, first.easeFactor());
        assertEquals(now.plus(Duration.ofDays(1)), first.nextReviewAt());

        FlashcardReview lowEfPrev = FlashcardReview.builder()
                .easeFactor(1.32)
                .repetitions(1)
                .intervalDays(1)
                .build();
        Sm2Schedule second = calculator.calculate(lowEfPrev, FlashcardRating.HARD, now);
        assertEquals(2, second.repetitions());
        assertEquals(3, second.intervalDays());
        assertEquals(1.3, second.easeFactor());
    }

    @Test
    @DisplayName("OK follows SM-2 intervals 1 -> 6 -> interval*EF while keeping easeFactor steady")
    void calculate_Ok_FollowsStandardSm2Intervals() {
        Instant now = Instant.parse("2026-11-25T14:00:00Z");

        Sm2Schedule r1 = calculator.calculate(null, FlashcardRating.OK, now);
        assertEquals(1, r1.repetitions());
        assertEquals(1, r1.intervalDays());
        assertEquals(2.5, r1.easeFactor());

        FlashcardReview prev1 = FlashcardReview.builder()
                .easeFactor(r1.easeFactor())
                .repetitions(r1.repetitions())
                .intervalDays(r1.intervalDays())
                .build();
        Sm2Schedule r2 = calculator.calculate(prev1, FlashcardRating.OK, now);
        assertEquals(2, r2.repetitions());
        assertEquals(6, r2.intervalDays());
        assertEquals(2.5, r2.easeFactor());

        FlashcardReview prev2 = FlashcardReview.builder()
                .easeFactor(r2.easeFactor())
                .repetitions(r2.repetitions())
                .intervalDays(r2.intervalDays())
                .build();
        Sm2Schedule r3 = calculator.calculate(prev2, FlashcardRating.OK, now);
        assertEquals(3, r3.repetitions());
        assertEquals(15, r3.intervalDays());
        assertEquals(now.plus(Duration.ofDays(15)), r3.nextReviewAt());
    }

    @Test
    @DisplayName("EASY expands intervals faster and increases easeFactor")
    void calculate_Easy_IncreasesEaseFactorAndIntervals() {
        Instant now = Instant.parse("2026-11-25T14:00:00Z");

        Sm2Schedule first = calculator.calculate(null, FlashcardRating.EASY, now);
        assertEquals(1, first.repetitions());
        assertEquals(4, first.intervalDays());
        assertEquals(2.6, first.easeFactor());
        assertEquals(now.plus(Duration.ofDays(4)), first.nextReviewAt());

        FlashcardReview prev = FlashcardReview.builder()
                .easeFactor(first.easeFactor())
                .repetitions(first.repetitions())
                .intervalDays(first.intervalDays())
                .build();
        Sm2Schedule second = calculator.calculate(prev, FlashcardRating.EASY, now);
        assertEquals(2, second.repetitions());
        assertEquals(10, second.intervalDays());
        assertTrue(second.easeFactor() > first.easeFactor());
    }
}
