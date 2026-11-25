package com.aistudy.assistant.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDetailResponse {

    private Long id;
    private Long courseId;
    private String title;
    private Integer position;
    private List<TopicResponse> topics;
}
