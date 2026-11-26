package com.aistudy.assistant.learning.flashcard.service;

import com.aistudy.assistant.learning.flashcard.entity.FlashcardRating;
import com.aistudy.assistant.learning.flashcard.entity.FlashcardReview;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure SM-2 spaced repetition interval and ease-factor calculator.
 */
@Component
public class SpacedRepetitionCalculator {

    public static final double DEFAULT_EASE_FACTOR = 2.5;
    public static final double MIN_EASE_FACTOR = 1.3;
    private static final Duration FORGOT_REQUEUE_DELAY = Duration.ofMinutes(10);

    public record Sm2Schedule(
            double easeFactor,
            int intervalDays,
            int repetitions,
            Instant nextReviewAt
    ) {
    }

    public Sm2Schedule calculate(FlashcardReview previousReview, FlashcardRating rating, Instant now) {
        double prevEf = previousReview != null && previousReview.getEaseFactor() != null
                ? previousReview.getEaseFactor()
                : DEFAULT_EASE_FACTOR;
        int prevReps = previousReview != null && previousReview.getRepetitions() != null
                ? previousReview.getRepetitions()
                : 0;
        int prevInterval = previousReview != null && previousReview.getIntervalDays() != null
                ? previousReview.getIntervalDays()
                : 0;

        if (rating == FlashcardRating.FORGOT) {
            double newEf = roundTwoDecimals(Math.max(MIN_EASE_FACTOR, prevEf - 0.20));
            return new Sm2Schedule(newEf, 0, 0, now.plus(FORGOT_REQUEUE_DELAY));
        }

        int quality = switch (rating) {
            case HARD -> 3;
            case OK -> 4;
            case EASY -> 5;
            case FORGOT -> 1;
        };

        double delta = 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02);
        double newEf = roundTwoDecimals(Math.max(MIN_EASE_FACTOR, prevEf + delta));
        int newReps = prevReps + 1;

        int newInterval = switch (rating) {
            case HARD -> {
                if (prevReps == 0) {
                    yield 1;
                }
                if (prevReps == 1) {
                    yield 3;
                }
                yield Math.max(prevInterval + 1, (int) Math.round(prevInterval * 1.2));
            }
            case OK -> {
                if (prevReps == 0) {
                    yield 1;
                }
                if (prevReps == 1) {
                    yield 6;
                }
                yield Math.max(prevInterval + 1, (int) Math.round(prevInterval * newEf));
            }
            case EASY -> {
                if (prevReps == 0) {
                    yield 4;
                }
                if (prevReps == 1) {
                    yield 10;
                }
                yield Math.max(prevInterval + 2, (int) Math.round(prevInterval * newEf * 1.3));
            }
            case FORGOT -> 0;
        };

        Instant nextReviewAt = now.plus(Duration.ofDays(newInterval));
        return new Sm2Schedule(newEf, newInterval, newReps, nextReviewAt);
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
