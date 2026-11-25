package com.aistudy.assistant.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmCalendarImportRequest {

    @NotNull(message = "Danh sách chỉ mục sự kiện được chọn không được null.")
    private List<Integer> acceptedItemIndices;
}
