package com.aistudy.assistant.classroom.controller;

import com.aistudy.assistant.classroom.dto.AddCoTeacherRequest;
import com.aistudy.assistant.classroom.dto.ClassJoinRequestResponse;
import com.aistudy.assistant.classroom.dto.ClassMaterialResponse;
import com.aistudy.assistant.classroom.dto.ClassMemberResponse;
import com.aistudy.assistant.classroom.dto.ClassPostResponse;
import com.aistudy.assistant.classroom.dto.ClassroomResponse;
import com.aistudy.assistant.classroom.dto.CreateClassPostRequest;
import com.aistudy.assistant.classroom.dto.CreateClassroomRequest;
import com.aistudy.assistant.classroom.dto.CreateJoinRequestRequest;
import com.aistudy.assistant.classroom.dto.DecideJoinRequestRequest;
import com.aistudy.assistant.classroom.dto.PinClassPostRequest;
import com.aistudy.assistant.classroom.dto.UpdateClassroomRequest;
import com.aistudy.assistant.classroom.dto.UpdateClassroomStatusRequest;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.entity.JoinRequestStatus;
import com.aistudy.assistant.classroom.service.ClassroomService;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
 * REST endpoints for Classroom (FR-12): class creation, join requests, membership,
 * co-teachers, announcement feed, and access-controlled class materials.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Classrooms V2", description = "Quản lý lớp học, yêu cầu tham gia, thành viên, bảng tin và tài liệu lớp")
@SecurityRequirement(name = "BearerAuth")
public class ClassroomController {

    private final ClassroomService classroomService;

    @PostMapping("/classes")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "Tạo lớp học mới (sinh mã lớp duy nhất)")
    public ResponseEntity<ApiResponse<ClassroomResponse>> createClassroom(
            @Valid @RequestBody CreateClassroomRequest request) {
        ClassroomResponse response = classroomService.createClassroom(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/classes")
    @Operation(summary = "Danh sách lớp học mà người dùng đang là thành viên ACTIVE")
    public ResponseEntity<ApiResponse<List<ClassroomResponse>>> getMyClassrooms(
            @RequestParam(value = "status", required = false) ClassroomStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ClassroomResponse> page = classroomService.getMyClassrooms(status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @GetMapping("/classes/{id}")
    @Operation(summary = "Xem chi tiết lớp học (kiểm tra thành viên ACTIVE)")
    public ResponseEntity<ApiResponse<ClassroomResponse>> getClassroomDetail(@PathVariable Long id) {
        ClassroomResponse response = classroomService.getClassroomDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/classes/{id}")
    @Operation(summary = "Cập nhật thông tin lớp học (kiểm tra version hoặc header If-Match)")
    public ResponseEntity<ApiResponse<ClassroomResponse>> updateClassroom(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassroomRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        ClassroomResponse response = classroomService.updateClassroom(id, request, ifMatchVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/classes/{id}/status")
    @Operation(summary = "Đổi trạng thái lớp học (OPEN, CLOSED, ARCHIVED)")
    public ResponseEntity<ApiResponse<ClassroomResponse>> updateClassroomStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassroomStatusRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        ClassroomResponse response = classroomService.updateClassroomStatus(id, request, ifMatchVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/classes/join-requests")
    @Operation(summary = "Gửi yêu cầu tham gia lớp học bằng mã lớp")
    public ResponseEntity<ApiResponse<ClassJoinRequestResponse>> requestJoin(
            @Valid @RequestBody CreateJoinRequestRequest request) {
        ClassJoinRequestResponse response = classroomService.requestJoin(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/classes/{id}/join-requests")
    @Operation(summary = "Danh sách yêu cầu tham gia lớp (mặc định lọc PENDING)")
    public ResponseEntity<ApiResponse<List<ClassJoinRequestResponse>>> getJoinRequests(
            @PathVariable Long id,
            @RequestParam(value = "status", required = false, defaultValue = "PENDING") JoinRequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ClassJoinRequestResponse> page = classroomService.getJoinRequests(id, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PutMapping("/classes/{id}/join-requests/{reqId}")
    @Operation(summary = "Duyệt (APPROVED) hoặc từ chối (REJECTED) yêu cầu tham gia lớp")
    public ResponseEntity<ApiResponse<ClassJoinRequestResponse>> decideJoinRequest(
            @PathVariable Long id,
            @PathVariable Long reqId,
            @Valid @RequestBody DecideJoinRequestRequest request) {
        ClassJoinRequestResponse response = classroomService.decideJoinRequest(id, reqId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/classes/{id}/members")
    @Operation(summary = "Danh sách thành viên ACTIVE trong lớp")
    public ResponseEntity<ApiResponse<List<ClassMemberResponse>>> getMembers(
            @PathVariable Long id,
            @RequestParam(value = "role", required = false) ClassRole role,
            @PageableDefault(size = 20, sort = "joinedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ClassMemberResponse> page = classroomService.getMembers(id, role, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PostMapping("/classes/{id}/co-teachers")
    @Operation(summary = "Giảng viên chủ nhiệm thêm giảng viên đồng giảng dạy")
    public ResponseEntity<ApiResponse<ClassMemberResponse>> addCoTeacher(
            @PathVariable Long id,
            @Valid @RequestBody AddCoTeacherRequest request) {
        ClassMemberResponse response = classroomService.addCoTeacher(id, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @DeleteMapping("/classes/{id}/members/{userId}")
    @Operation(summary = "Giảng viên gỡ thành viên khỏi lớp (chuyển status = REMOVED)")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId) {
        classroomService.removeMember(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/classes/{id}/leave")
    @Operation(summary = "Thành viên tự rời lớp (chuyển status = LEFT)")
    public ResponseEntity<ApiResponse<Void>> leaveClassroom(@PathVariable Long id) {
        classroomService.leaveClassroom(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/classes/{id}/posts")
    @Operation(summary = "Xem bảng tin lớp học (ưu tiên bài ghim)")
    public ResponseEntity<ApiResponse<List<ClassPostResponse>>> getPosts(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ClassPostResponse> page = classroomService.getPosts(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PostMapping("/classes/{id}/posts")
    @Operation(summary = "Giảng viên đăng bài thông báo lên bảng tin lớp")
    public ResponseEntity<ApiResponse<ClassPostResponse>> createPost(
            @PathVariable Long id,
            @Valid @RequestBody CreateClassPostRequest request) {
        ClassPostResponse response = classroomService.createPost(id, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/classes/{id}/posts/{postId}/pin")
    @Operation(summary = "Ghim hoặc bỏ ghim bài đăng trên bảng tin lớp")
    public ResponseEntity<ApiResponse<ClassPostResponse>> pinPost(
            @PathVariable Long id,
            @PathVariable Long postId,
            @RequestBody(required = false) PinClassPostRequest request) {
        ClassPostResponse response = classroomService.pinPost(id, postId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/classes/{id}/materials")
    @Operation(summary = "Danh sách tài liệu của lớp học")
    public ResponseEntity<ApiResponse<List<ClassMaterialResponse>>> getMaterials(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ClassMaterialResponse> page = classroomService.getMaterials(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PostMapping(value = "/classes/{id}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Giảng viên tải tài liệu lên lớp học")
    public ResponseEntity<ApiResponse<ClassMaterialResponse>> uploadMaterial(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        ClassMaterialResponse response = classroomService.uploadMaterial(id, file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/class-materials/{id}/download")
    @Operation(summary = "Tải tài liệu lớp học (kiểm tra thành viên ACTIVE)")
    public ResponseEntity<Resource> downloadMaterial(@PathVariable Long id) {
        ClassroomService.ClassMaterialDownload download = classroomService.downloadMaterial(id);
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
