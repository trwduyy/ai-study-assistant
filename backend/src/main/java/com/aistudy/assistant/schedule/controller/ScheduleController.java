package com.aistudy.assistant.schedule.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.schedule.dto.CalendarEventResponse;
import com.aistudy.assistant.schedule.dto.CalendarView;
import com.aistudy.assistant.schedule.dto.CreatePersonalEventRequest;
import com.aistudy.assistant.schedule.dto.PersonalEventResponse;
import com.aistudy.assistant.schedule.dto.UpdatePersonalEventRequest;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import com.aistudy.assistant.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST endpoints for unified calendar and personal events (FR-15, FR-16).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Schedule & Calendar V2", description = "Lịch tổng hợp và quản lý sự kiện cá nhân")
@SecurityRequirement(name = "BearerAuth")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/calendar")
    @Operation(summary = "Lịch tổng hợp (cá nhân, Google Calendar và các lớp học đang tham gia)")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getCalendar(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "view", required = false, defaultValue = "WEEK") String view) {
        CalendarView calendarView = CalendarView.parseOrDefault(view);
        List<CalendarEventResponse> events = scheduleService.getCalendar(from, to, calendarView);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/personal-events")
    @Operation(summary = "Danh sách sự kiện cá nhân của người dùng hiện tại")
    public ResponseEntity<ApiResponse<List<PersonalEventResponse>>> getPersonalEvents(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "startAt", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<PersonalEventResponse> page = scheduleService.getPersonalEvents(from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PostMapping("/personal-events")
    @Operation(summary = "Tạo sự kiện cá nhân mới")
    public ResponseEntity<ApiResponse<PersonalEventResponse>> createPersonalEvent(
            @Valid @RequestBody CreatePersonalEventRequest request) {
        PersonalEventResponse response = scheduleService.createPersonalEvent(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/personal-events/{id}")
    @Operation(summary = "Sửa sự kiện cá nhân (kiểm tra chủ sở hữu và version)")
    public ResponseEntity<ApiResponse<PersonalEventResponse>> updatePersonalEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePersonalEventRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = EventValidationSupport.parseIfMatchVersion(ifMatchHeader);
        PersonalEventResponse response = scheduleService.updatePersonalEvent(id, request, ifMatchVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/personal-events/{id}")
    @Operation(summary = "Xóa (hủy) sự kiện cá nhân (kiểm tra chủ sở hữu)")
    public ResponseEntity<ApiResponse<Void>> deletePersonalEvent(@PathVariable Long id) {
        scheduleService.deletePersonalEvent(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
