package com.aistudy.assistant.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Classroom timed test summary DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassTestResponse {

    private Long id;
    private Long classId;
    private Long quizId;
    private String title;
    private Instant startAt;
    private Instant endAt;
    private Integer durationMinutes;
    private Integer maxAttempts;
    private Boolean shuffleQuestions;
    private Long myAttemptsUsed;
    private Instant createdAt;
    private Instant updatedAt;
}
