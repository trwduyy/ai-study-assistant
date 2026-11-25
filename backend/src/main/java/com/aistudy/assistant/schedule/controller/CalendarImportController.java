package com.aistudy.assistant.schedule.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.schedule.dto.CalendarImportPreviewResponse;
import com.aistudy.assistant.schedule.dto.ConfirmCalendarImportRequest;
import com.aistudy.assistant.schedule.dto.ConfirmCalendarImportResponse;
import com.aistudy.assistant.schedule.service.CalendarImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST endpoints for importing {@code .ics} and {@code .csv} schedules with overlap preview (FR-15).
 */
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
@Tag(name = "Calendar Imports V2", description = "Tải mẫu CSV và nhập lịch từ .ics/.csv có xem trước và cảnh báo trùng giờ")
@SecurityRequirement(name = "BearerAuth")
public class CalendarImportController {

    private final CalendarImportService calendarImportService;

    @GetMapping("/import-template")
    @Operation(summary = "Tải file CSV mẫu chuẩn UTF-8 để nhập lịch")
    public ResponseEntity<Resource> downloadImportTemplate() {
        byte[] csvBytes = calendarImportService.getImportTemplateCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"calendar-template.csv\"")
                .contentLength(csvBytes.length)
                .body(new ByteArrayResource(csvBytes));
    }

    @PostMapping(value = "/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên tệp .ics hoặc .csv để xem trước và phát hiện trùng giờ")
    public ResponseEntity<ApiResponse<CalendarImportPreviewResponse>> previewImport(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "defaultTimezone", required = false, defaultValue = "Asia/Ho_Chi_Minh") String defaultTimezone) {
        CalendarImportPreviewResponse response = calendarImportService.previewImport(file, defaultTimezone);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/imports/{importId}/confirm")
    @Operation(summary = "Xác nhận nhập các sự kiện đã chọn từ bản xem trước")
    public ResponseEntity<ApiResponse<ConfirmCalendarImportResponse>> confirmImport(
            @PathVariable Long importId,
            @Valid @RequestBody ConfirmCalendarImportRequest request) {
        ConfirmCalendarImportResponse response = calendarImportService.confirmImport(importId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
