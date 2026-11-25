package com.aistudy.assistant.dto.quiz;

import com.aistudy.assistant.entity.AttemptAnswer;
import com.aistudy.assistant.entity.OptionLetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptAnswerDetailResponse {

    private Long questionId;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private OptionLetter selectedOption;
    private OptionLetter correctOption;
    @JsonProperty("isCorrect")
    private boolean isCorrect;
    private String explanation;

    public static AttemptAnswerDetailResponse fromEntity(AttemptAnswer answer) {
        if (answer == null || answer.getQuestion() == null) {
            return null;
        }

        return AttemptAnswerDetailResponse.builder()
                .questionId(answer.getQuestion().getId())
                .questionText(answer.getQuestion().getQuestionText())
                .optionA(answer.getQuestion().getOptionA())
                .optionB(answer.getQuestion().getOptionB())
                .optionC(answer.getQuestion().getOptionC())
                .optionD(answer.getQuestion().getOptionD())
                .selectedOption(answer.getSelectedOption())
                .correctOption(answer.getQuestion().getCorrectOption())
                .isCorrect(Boolean.TRUE.equals(answer.getIsCorrect()))
                .explanation(answer.getQuestion().getExplanation())
                .build();
    }
}
