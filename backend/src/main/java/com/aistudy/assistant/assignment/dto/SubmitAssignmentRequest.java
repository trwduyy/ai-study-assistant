package com.aistudy.assistant.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON request payload for submitting a text-only assignment answer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAssignmentRequest {

    private String textAnswer;
}
