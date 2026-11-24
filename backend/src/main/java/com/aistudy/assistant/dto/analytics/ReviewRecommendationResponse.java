package com.aistudy.assistant.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRecommendationResponse {

    private Long courseId;
    private String courseTitle;
    private String recommendedTopic;
    private String reason;
    private String sampleQuestion;
    private String suggestedAction;
}
