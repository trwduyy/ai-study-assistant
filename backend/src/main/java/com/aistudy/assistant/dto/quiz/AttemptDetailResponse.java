package com.aistudy.assistant.dto.quiz;

import com.aistudy.assistant.entity.QuizAttempt;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptDetailResponse {

    private Long attemptId;
    private Long quizId;
    private String quizTitle;
    private BigDecimal score;
    private Integer totalQuestions;
    private Integer correctCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant submittedAt;

    @Builder.Default
    private List<AttemptAnswerDetailResponse> answers = new ArrayList<>();

    public static AttemptDetailResponse fromEntity(QuizAttempt attempt) {
        if (attempt == null) {
            return null;
        }

        List<AttemptAnswerDetailResponse> answerResponses = attempt.getAnswers() != null
                ? attempt.getAnswers().stream()
                .map(AttemptAnswerDetailResponse::fromEntity)
                .collect(Collectors.toList())
                : new ArrayList<>();

        return AttemptDetailResponse.builder()
                .attemptId(attempt.getId())
                .quizId(attempt.getQuiz() != null ? attempt.getQuiz().getId() : null)
                .quizTitle(attempt.getQuiz() != null ? attempt.getQuiz().getTitle() : null)
                .score(attempt.getScore())
                .totalQuestions(attempt.getTotalQuestions())
                .correctCount(attempt.getCorrectCount())
                .submittedAt(attempt.getSubmittedAt())
                .answers(answerResponses)
                .build();
    }
}
