package com.aistudy.assistant.dto.quiz;

import com.aistudy.assistant.entity.OptionLetter;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionAnswerSubmit {

    @NotNull(message = "Question ID is required")
    private Long questionId;

    private OptionLetter selectedOption;
}
