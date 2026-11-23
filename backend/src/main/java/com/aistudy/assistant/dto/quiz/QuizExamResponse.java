package com.aistudy.assistant.dto.quiz;

import com.aistudy.assistant.entity.Difficulty;
import com.aistudy.assistant.entity.Quiz;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizExamResponse {

    private Long id;
    private String title;
    private Difficulty difficulty;
    private Integer questionCount;

    @Builder.Default
    private List<QuizQuestionExamResponse> questions = new ArrayList<>();

    public static QuizExamResponse fromEntity(Quiz quiz) {
        if (quiz == null) {
            return null;
        }

        List<QuizQuestionExamResponse> questionList = quiz.getQuestions() != null
                ? quiz.getQuestions().stream()
                .map(QuizQuestionExamResponse::fromEntity)
                .collect(Collectors.toList())
                : new ArrayList<>();

        return QuizExamResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .difficulty(quiz.getDifficulty())
                .questionCount(quiz.getQuestionCount())
                .questions(questionList)
                .build();
    }
}
