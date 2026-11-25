package com.aistudy.assistant.meeting.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.meeting.dto.ClassEventResponse;
import com.aistudy.assistant.meeting.dto.CreateClassEventRequest;
import com.aistudy.assistant.meeting.dto.UpdateClassEventRequest;
import com.aistudy.assistant.meeting.service.ClassEventService;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
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
 * REST endpoints for classroom events and meetings (FR-15).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Class Events & Meetings V2", description = "Quản lý sự kiện học tập, kiểm tra và cuộc họp trong lớp học")
@SecurityRequirement(name = "BearerAuth")
public class ClassEventController {

    private final ClassEventService classEventService;

    @PostMapping("/classes/{id}/events")
    @Operation(summary = "Giảng viên tạo sự kiện lớp học / kiểm tra / cuộc họp")
    public ResponseEntity<ApiResponse<ClassEventResponse>> createClassEvent(
            @PathVariable Long id,
            @Valid @RequestBody CreateClassEventRequest request) {
        ClassEventResponse response = classEventService.createClassEvent(id, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/classes/{id}/events")
    @Operation(summary = "Danh sách sự kiện của lớp học (thành viên ACTIVE)")
    public ResponseEntity<ApiResponse<List<ClassEventResponse>>> getClassEvents(
            @PathVariable Long id,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "startAt", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ClassEventResponse> page = classEventService.getClassEvents(id, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PutMapping("/events/{id}")
    @Operation(summary = "Giảng viên sửa sự kiện lớp học (kiểm tra version hoặc header If-Match)")
    public ResponseEntity<ApiResponse<ClassEventResponse>> updateClassEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassEventRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = EventValidationSupport.parseIfMatchVersion(ifMatchHeader);
        ClassEventResponse response = classEventService.updateClassEvent(id, request, ifMatchVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/events/{id}")
    @Operation(summary = "Giảng viên hủy sự kiện lớp học (chuyển status = CANCELLED)")
    public ResponseEntity<ApiResponse<Void>> cancelClassEvent(@PathVariable Long id) {
        classEventService.cancelClassEvent(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
