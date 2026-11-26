package com.aistudy.assistant.admin.controller;

import com.aistudy.assistant.admin.dto.CreateReportRequest;
import com.aistudy.assistant.admin.dto.ReportResponse;
import com.aistudy.assistant.admin.service.ReportService;
import com.aistudy.assistant.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint allowing any authenticated user to submit moderation or AI inaccuracy reports.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports V2", description = "Gửi báo cáo nội dung hoặc phản hồi sự cố")
@SecurityRequirement(name = "BearerAuth")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "Gửi báo cáo vi phạm hoặc nội dung AI chưa chính xác")
    public ResponseEntity<ApiResponse<ReportResponse>> createReport(
            @Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reportService.createReport(request)));
    }
}
