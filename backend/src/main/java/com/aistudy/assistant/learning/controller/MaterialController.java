package com.aistudy.assistant.learning.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.learning.dto.MaterialChunkResponse;
import com.aistudy.assistant.learning.dto.MaterialDetailResponse;
import com.aistudy.assistant.learning.dto.MaterialResponse;
import com.aistudy.assistant.learning.dto.UpdateMaterialRequest;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.service.MaterialService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST endpoints for study material upload, async text extraction inspection, download, and soft deletion.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Materials & Extraction V2", description = "Tải lên, trích xuất bất đồng bộ và quản lý tài liệu học tập")
@SecurityRequirement(name = "BearerAuth")
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping(value = "/courses/{courseId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên tài liệu (PDF, DOCX, TXT) và kích hoạt trích xuất bất đồng bộ")
    public ResponseEntity<ApiResponse<MaterialResponse>> uploadMaterial(
            @PathVariable Long courseId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "topicId", required = false) Long topicId,
            @RequestParam(value = "category", required = false) String category) {
        MaterialResponse response = materialService.uploadMaterial(courseId, file, topicId, category);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/courses/{courseId}/materials")
    @Operation(summary = "Danh sách tài liệu trong khóa học (lọc theo topicId, category, status, phân trang)")
    public ResponseEntity<ApiResponse<List<MaterialResponse>>> getMaterials(
            @PathVariable Long courseId,
            @RequestParam(value = "topicId", required = false) Long topicId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) MaterialStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MaterialResponse> page = materialService.getMaterials(courseId, topicId, category, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @GetMapping("/materials/{id}")
    @Operation(summary = "Xem thông tin chi tiết và trạng thái trích xuất của tài liệu")
    public ResponseEntity<ApiResponse<MaterialDetailResponse>> getMaterialDetail(@PathVariable Long id) {
        MaterialDetailResponse response = materialService.getMaterialDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/materials/{id}/content")
    @Operation(summary = "Xem nội dung văn bản đã trích xuất (phân trang theo chunk/trang)")
    public ResponseEntity<ApiResponse<List<MaterialChunkResponse>>> getMaterialContent(
            @PathVariable Long id,
            @RequestParam(value = "pageFrom", required = false) Integer pageFrom,
            @RequestParam(value = "pageTo", required = false) Integer pageTo,
            @PageableDefault(size = 10, sort = "chunkIndex", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<MaterialChunkResponse> page = materialService.getMaterialContent(id, pageFrom, pageTo, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @GetMapping("/materials/{id}/download")
    @Operation(summary = "Tải về tệp tài liệu gốc (kiểm tra quyền sở hữu)")
    public ResponseEntity<Resource> downloadMaterial(@PathVariable Long id) {
        MaterialService.MaterialDownload download = materialService.downloadMaterial(id);
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

    @PutMapping("/materials/{id}")
    @Operation(summary = "Cập nhật tên, phân loại hoặc chuyển chủ đề tài liệu")
    public ResponseEntity<ApiResponse<MaterialDetailResponse>> updateMaterial(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaterialRequest request) {
        MaterialDetailResponse response = materialService.updateMaterial(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/materials/{id}")
    @Operation(summary = "Xóa mềm tài liệu")
    public ResponseEntity<ApiResponse<Void>> deleteMaterial(@PathVariable Long id) {
        materialService.deleteMaterial(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/materials/{id}/reextract")
    @Operation(summary = "Kích hoạt trích xuất lại văn bản bất đồng bộ")
    public ResponseEntity<ApiResponse<MaterialDetailResponse>> reextractMaterial(@PathVariable Long id) {
        MaterialDetailResponse response = materialService.reextractMaterial(id);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response));
    }
}
