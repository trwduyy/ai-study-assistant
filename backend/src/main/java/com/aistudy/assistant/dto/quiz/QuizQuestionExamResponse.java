package com.aistudy.assistant.dto.quiz;

import com.aistudy.assistant.entity.QuizQuestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionExamResponse {

    private Long id;
    private Integer orderIndex;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;

    public static QuizQuestionExamResponse fromEntity(QuizQuestion question) {
        if (question == null) {
            return null;
        }

        return QuizQuestionExamResponse.builder()
                .id(question.getId())
                .orderIndex(question.getOrderIndex())
                .questionText(question.getQuestionText())
                .optionA(question.getOptionA())
                .optionB(question.getOptionB())
                .optionC(question.getOptionC())
                .optionD(question.getOptionD())
                .build();
    }
}
