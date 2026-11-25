package com.aistudy.assistant.assignment.controller;

import com.aistudy.assistant.assignment.dto.ClassTestResponse;
import com.aistudy.assistant.assignment.dto.ClassTestResultsResponse;
import com.aistudy.assistant.assignment.dto.ClassTestSubmitResultResponse;
import com.aistudy.assistant.assignment.dto.CreateClassTestRequest;
import com.aistudy.assistant.assignment.dto.StartClassTestAttemptResponse;
import com.aistudy.assistant.assignment.dto.UpdateClassTestRequest;
import com.aistudy.assistant.assignment.service.ClassTestService;
import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.learning.dto.SubmitAttemptRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for timed classroom tests (FR-14).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Class Tests V2", description = "Bài kiểm tra định thời trong lớp học")
@SecurityRequirement(name = "BearerAuth")
public class ClassTestController {

    private final ClassTestService classTestService;

    @PostMapping("/classes/{id}/tests")
    @Operation(summary = "Giảng viên tạo bài kiểm tra định thời từ quiz")
    public ResponseEntity<ApiResponse<ClassTestResponse>> createTest(
            @PathVariable Long id,
            @Valid @RequestBody CreateClassTestRequest request) {
        ClassTestResponse response = classTestService.createTest(id, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/classes/{id}/tests")
    @Operation(summary = "Danh sách bài kiểm tra của lớp (thành viên lớp)")
    public ResponseEntity<ApiResponse<List<ClassTestResponse>>> getTests(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "startAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ClassTestResponse> page = classTestService.getTests(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PutMapping("/tests/{id}")
    @Operation(summary = "Giảng viên cập nhật cấu hình bài kiểm tra")
    public ResponseEntity<ApiResponse<ClassTestResponse>> updateTest(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success(classTestService.updateTest(id, request)));
    }

    @DeleteMapping("/tests/{id}")
    @Operation(summary = "Giảng viên xóa bài kiểm tra")
    public ResponseEntity<ApiResponse<Void>> deleteTest(@PathVariable Long id) {
        classTestService.deleteTest(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/tests/{id}/attempts")
    @Operation(summary = "Sinh viên bắt đầu lượt làm bài kiểm tra")
    public ResponseEntity<ApiResponse<StartClassTestAttemptResponse>> startAttempt(@PathVariable Long id) {
        StartClassTestAttemptResponse response = classTestService.startAttempt(id);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/tests/{id}/attempts/{attemptId}/submit")
    @Operation(summary = "Sinh viên nộp bài kiểm tra (Idempotency-Key bắt buộc)")
    public ResponseEntity<ApiResponse<ClassTestSubmitResultResponse>> submitAttempt(
            @PathVariable Long id,
            @PathVariable Long attemptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) SubmitAttemptRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                classTestService.submitAttempt(id, attemptId, idempotencyKey, request)
        ));
    }

    @GetMapping("/tests/{id}/results")
    @Operation(summary = "Giảng viên xem bảng điểm tổng hợp cả lớp")
    public ResponseEntity<ApiResponse<ClassTestResultsResponse>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(classTestService.getResults(id)));
    }
}
