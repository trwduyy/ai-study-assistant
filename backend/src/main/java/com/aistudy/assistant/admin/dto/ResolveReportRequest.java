package com.aistudy.assistant.admin.dto;

import com.aistudy.assistant.admin.entity.ReportStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveReportRequest {

    private ReportStatus status;

    @Size(max = 2000, message = "Ghi chú xử lý tối đa 2000 ký tự.")
    private String resolutionNote;
}
