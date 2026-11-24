package com.aistudy.assistant.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitQuizRequest {

    @NotEmpty(message = "Answers list cannot be empty")
    @Valid
    private List<QuestionAnswerSubmit> answers;
}
