package com.aistudy.assistant.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialChunkResponse {

    private Long chunkId;
    private Integer chunkIndex;
    private Integer pageFrom;
    private Integer pageTo;
    private String content;
    private Integer tokenCount;
}
