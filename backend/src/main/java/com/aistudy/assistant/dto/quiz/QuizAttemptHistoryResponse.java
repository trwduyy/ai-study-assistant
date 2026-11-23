package com.aistudy.assistant.dto.quiz;

import com.aistudy.assistant.entity.QuizAttempt;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptHistoryResponse {

    private Long attemptId;
    private BigDecimal score;
    private Integer correctCount;
    private Integer totalQuestions;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant submittedAt;

    public static QuizAttemptHistoryResponse fromEntity(QuizAttempt attempt) {
        if (attempt == null) {
            return null;
        }

        return QuizAttemptHistoryResponse.builder()
                .attemptId(attempt.getId())
                .score(attempt.getScore())
                .correctCount(attempt.getCorrectCount())
                .totalQuestions(attempt.getTotalQuestions())
                .submittedAt(attempt.getSubmittedAt())
                .build();
    }
}
