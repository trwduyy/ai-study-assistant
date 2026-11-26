package com.aistudy.assistant.schedule.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.schedule.dto.DecideStudyPlanItemRequest;
import com.aistudy.assistant.schedule.dto.StudyPlanItemResponse;
import com.aistudy.assistant.schedule.dto.StudyPlanResponse;
import com.aistudy.assistant.schedule.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for reviewing and accepting AI-proposed study plans (FR-17, BR-11).
 */
@RestController
@RequestMapping("/api/v1/study-plans")
@RequiredArgsConstructor
@Tag(name = "AI Study Plans V2", description = "Xem và duyệt từng buổi học trong lộ trình học tập do AI đề xuất")
@SecurityRequirement(name = "BearerAuth")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết đề xuất lộ trình học tập kèm cảnh báo trùng lịch")
    public ResponseEntity<ApiResponse<StudyPlanResponse>> getStudyPlan(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(studyPlanService.getStudyPlan(id)));
    }

    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "Chấp nhận (ACCEPTED), chỉnh sửa (EDITED) hoặc từ chối (REJECTED) một mục đề xuất")
    public ResponseEntity<ApiResponse<StudyPlanItemResponse>> decidePlanItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody DecideStudyPlanItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(studyPlanService.decidePlanItem(id, itemId, request)));
    }
}
