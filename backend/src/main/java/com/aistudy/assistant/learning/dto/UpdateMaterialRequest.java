package com.aistudy.assistant.learning.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMaterialRequest {

    @Size(min = 1, max = 255, message = "Tên tài liệu phải từ 1 đến 255 ký tự")
    private String fileName;

    private Long topicId;

    private Boolean clearTopic;

    @Size(max = 60, message = "Phân loại tài liệu tối đa 60 ký tự")
    private String category;
}
