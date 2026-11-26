package com.aistudy.assistant.schedule.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.schedule.dto.CreateGoalRequest;
import com.aistudy.assistant.schedule.dto.GoalResponse;
import com.aistudy.assistant.schedule.dto.UpdateGoalRequest;
import com.aistudy.assistant.schedule.entity.GoalStatus;
import com.aistudy.assistant.schedule.service.GoalService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for study goals (FR-17).
 */
@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
@Tag(name = "Goals V2", description = "Quản lý mục tiêu học tập cá nhân")
@SecurityRequirement(name = "BearerAuth")
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    @Operation(summary = "Danh sách mục tiêu học tập (lọc theo status, phân trang)")
    public ResponseEntity<ApiResponse<List<GoalResponse>>> listGoals(
            @RequestParam(value = "status", required = false) GoalStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GoalResponse> page = goalService.listGoals(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PostMapping
    @Operation(summary = "Tạo mục tiêu học tập mới")
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(@Valid @RequestBody CreateGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(goalService.createGoal(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật mục tiêu học tập")
    public ResponseEntity<ApiResponse<GoalResponse>> updateGoal(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGoalRequest request) {
        return ResponseEntity.ok(ApiResponse.success(goalService.updateGoal(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa mục tiêu học tập")
    public ResponseEntity<ApiResponse<Void>> deleteGoal(@PathVariable Long id) {
        goalService.deleteGoal(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
