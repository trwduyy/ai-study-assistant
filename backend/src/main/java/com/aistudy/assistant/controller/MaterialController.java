package com.aistudy.assistant.controller;

import com.aistudy.assistant.common.ApiResponse;
import com.aistudy.assistant.dto.material.MaterialDetailResponse;
import com.aistudy.assistant.dto.material.MaterialResponse;
import com.aistudy.assistant.dto.material.MaterialUploadResponse;
import com.aistudy.assistant.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
@Tag(name = "Material Management", description = "Endpoints for uploading, inspecting, downloading, and deleting study documents")
@SecurityRequirement(name = "BearerAuth")
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a study document (PDF, DOCX, TXT) with automatic text extraction")
    public ResponseEntity<ApiResponse<MaterialUploadResponse>> uploadMaterial(
            @Parameter(description = "Study document file (max 20MB)")
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "ID of the course this material belongs to")
            @RequestParam("courseId") Long courseId,
            @Parameter(description = "Optional ID of the topic within the course")
            @RequestParam(value = "topicId", required = false) Long topicId) {

        MaterialUploadResponse response = materialService.uploadMaterial(file, courseId, topicId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Material uploaded and text extracted successfully"));
    }

    @GetMapping
    @Operation(summary = "List study materials with optional filtering by courseId and topicId")
    public ResponseEntity<ApiResponse<List<MaterialResponse>>> getMaterials(
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "topicId", required = false) Long topicId) {

        List<MaterialResponse> materials = materialService.getMaterials(courseId, topicId);
        return ResponseEntity.ok(ApiResponse.success(materials, "Materials retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get material details including extracted text")
    public ResponseEntity<ApiResponse<MaterialDetailResponse>> getMaterialDetail(@PathVariable Long id) {
        MaterialDetailResponse response = materialService.getMaterialDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Material details retrieved"));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download the original uploaded file")
    public ResponseEntity<Resource> downloadMaterial(@PathVariable Long id) {
        Resource fileResource = materialService.downloadMaterial(id);
        String originalFileName = materialService.getOriginalFileName(id);
        String encodedFilename = URLEncoder.encode(originalFileName, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFileName + "\"; filename*=UTF-8''" + encodedFilename)
                .body(fileResource);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a study material and its physical storage file")
    public ResponseEntity<ApiResponse<Void>> deleteMaterial(@PathVariable Long id) {
        materialService.deleteMaterial(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Material deleted successfully"));
    }
}
