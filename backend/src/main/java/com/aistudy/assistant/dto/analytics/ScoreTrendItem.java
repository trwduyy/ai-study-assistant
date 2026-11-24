package com.aistudy.assistant.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreTrendItem {

    private Long quizId;
    private String quizTitle;
    private BigDecimal score;
    private String date;
}
