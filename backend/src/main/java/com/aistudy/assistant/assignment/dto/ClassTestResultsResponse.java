package com.aistudy.assistant.assignment.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated classroom test results report for teachers.
 */
public record ClassTestResultsResponse(
        Long testId,
        Long classId,
        String title,
        long totalStudents,
        long submittedCount,
        double averageScore,
        double highestScore,
        double lowestScore,
        List<StudentTestResultDto> studentResults
) {
    public record StudentTestResultDto(
            Long studentId,
            String studentName,
            String studentEmail,
            long attemptsUsed,
            Double bestScore,
            Instant lastSubmittedAt
    ) {
    }
}
