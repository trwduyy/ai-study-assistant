package com.aistudy.assistant.assignment.controller;

import com.aistudy.assistant.assignment.dto.AssignmentResponse;
import com.aistudy.assistant.assignment.dto.AssignmentSummaryResponse;
import com.aistudy.assistant.assignment.dto.CreateAssignmentRequest;
import com.aistudy.assistant.assignment.dto.GradeSubmissionRequest;
import com.aistudy.assistant.assignment.dto.ReopenSubmissionRequest;
import com.aistudy.assistant.assignment.dto.SubmissionResponse;
import com.aistudy.assistant.assignment.dto.SubmissionSummaryResponse;
import com.aistudy.assistant.assignment.dto.SubmitAssignmentRequest;
import com.aistudy.assistant.assignment.dto.UpdateAssignmentRequest;
import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import com.aistudy.assistant.assignment.service.AssignmentService;
import com.aistudy.assistant.assignment.service.SubmissionService;
import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST endpoints for Assignments and Submissions (FR-13).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Assignments & Submissions V2", description = "Quản lý bài tập, nộp bài, chấm điểm và trả bài")
@SecurityRequirement(name = "BearerAuth")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;

    @PostMapping(value = "/classes/{id}/assignments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Giảng viên tạo bài tập mới (JSON)")
    public ResponseEntity<ApiResponse<AssignmentResponse>> createAssignmentJson(
            @PathVariable Long id,
            @Valid @RequestBody CreateAssignmentRequest request) {
        AssignmentResponse response = assignmentService.createAssignment(id, request, List.of());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping(value = "/classes/{id}/assignments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Giảng viên tạo bài tập mới kèm tệp đính kèm (multipart)")
    public ResponseEntity<ApiResponse<AssignmentResponse>> createAssignmentMultipart(
            @PathVariable Long id,
            @RequestPart("data") @Valid CreateAssignmentRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        AssignmentResponse response = assignmentService.createAssignment(id, request, files);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/classes/{id}/assignments")
    @Operation(summary = "Danh sách bài tập của lớp (thành viên lớp)")
    public ResponseEntity<ApiResponse<List<AssignmentSummaryResponse>>> getAssignments(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AssignmentSummaryResponse> page = assignmentService.getAssignments(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @GetMapping("/assignments/{id}")
    @Operation(summary = "Xem chi tiết bài tập")
    public ResponseEntity<ApiResponse<AssignmentResponse>> getAssignmentDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getAssignmentDetail(id)));
    }

    @PutMapping("/assignments/{id}")
    @Operation(summary = "Cập nhật bài tập (kiểm tra version hoặc header If-Match)")
    public ResponseEntity<ApiResponse<AssignmentResponse>> updateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssignmentRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        return ResponseEntity.ok(ApiResponse.success(assignmentService.updateAssignment(id, request, ifMatchVersion)));
    }

    @DeleteMapping("/assignments/{id}")
    @Operation(summary = "Xóa bài tập")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping(value = "/assignments/{id}/submissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sinh viên nộp bài tập dạng văn bản (Idempotency-Key bắt buộc)")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submitAssignmentJson(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) SubmitAssignmentRequest request) {
        String textAnswer = request != null ? request.getTextAnswer() : null;
        SubmissionResponse response = submissionService.submitAssignment(id, idempotencyKey, textAnswer, List.of());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping(value = "/assignments/{id}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sinh viên nộp bài tập kèm tệp (Idempotency-Key bắt buộc)")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submitAssignmentMultipart(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(value = "textAnswer", required = false) String textAnswer,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        SubmissionResponse response = submissionService.submitAssignment(id, idempotencyKey, textAnswer, files);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/assignments/{id}/submissions")
    @Operation(summary = "Giảng viên xem danh sách bài nộp của bài tập")
    public ResponseEntity<ApiResponse<List<SubmissionSummaryResponse>>> getSubmissions(
            @PathVariable Long id,
            @RequestParam(value = "status", required = false) SubmissionStatus status,
            @RequestParam(value = "late", required = false) Boolean late,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SubmissionSummaryResponse> page = submissionService.getSubmissions(id, status, late, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @GetMapping("/assignments/{id}/submissions/me")
    @Operation(summary = "Sinh viên xem bài nộp của bản thân")
    public ResponseEntity<ApiResponse<SubmissionResponse>> getMySubmission(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(submissionService.getMySubmission(id)));
    }

    @PutMapping("/submissions/{id}/grade")
    @Operation(summary = "Giảng viên chấm điểm, nhận xét và trả bài")
    public ResponseEntity<ApiResponse<SubmissionResponse>> gradeSubmission(
            @PathVariable Long id,
            @Valid @RequestBody GradeSubmissionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        return ResponseEntity.ok(ApiResponse.success(submissionService.gradeSubmission(id, request, ifMatchVersion)));
    }

    @PutMapping("/submissions/{id}/reopen")
    @Operation(summary = "Giảng viên mở lại quyền nộp bài cho sinh viên (theo D-02)")
    public ResponseEntity<ApiResponse<SubmissionResponse>> reopenSubmission(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReopenSubmissionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        return ResponseEntity.ok(ApiResponse.success(submissionService.reopenSubmission(id, request, ifMatchVersion)));
    }

    @GetMapping("/submissions/{id}/download")
    @Operation(summary = "Tải tệp bài nộp (chủ bài nộp hoặc giảng viên lớp)")
    public ResponseEntity<Resource> downloadSubmissionFile(
            @PathVariable Long id,
            @RequestParam(value = "fileId", required = false) Long fileId) {
        SubmissionService.SubmissionFileDownload download = submissionService.downloadSubmissionFile(id, fileId);
        String encodedFilename = URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        MediaType contentType = MediaType.parseMediaType(download.mimeType());

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.fileName() + "\"; filename*=UTF-8''" + encodedFilename
                )
                .body(download.resource());
    }

    private Integer parseIfMatchVersion(String ifMatchHeader) {
        if (!StringUtils.hasText(ifMatchHeader)) {
            return null;
        }
        String sanitized = ifMatchHeader.trim();
        if (sanitized.startsWith("W/")) {
            sanitized = sanitized.substring(2).trim();
        }
        if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length() >= 2) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        try {
            return Integer.parseInt(sanitized);
        } catch (NumberFormatException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header If-Match không hợp lệ.");
        }
    }
}
