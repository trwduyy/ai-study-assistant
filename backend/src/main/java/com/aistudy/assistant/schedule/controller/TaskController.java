package com.aistudy.assistant.schedule.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.schedule.dto.CompleteTaskRequest;
import com.aistudy.assistant.schedule.dto.CreateTaskRequest;
import com.aistudy.assistant.schedule.dto.TaskResponse;
import com.aistudy.assistant.schedule.dto.UpdateTaskRequest;
import com.aistudy.assistant.schedule.entity.TaskStatus;
import com.aistudy.assistant.schedule.service.TaskService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST endpoints for daily study tasks (FR-17).
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Daily Tasks V2", description = "Quản lý nhiệm vụ học tập theo ngày và nhắc việc")
@SecurityRequirement(name = "BearerAuth")
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "Danh sách task theo ngày (lọc theo from, to, status, goalId)")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> listTasks(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "status", required = false) TaskStatus status,
            @RequestParam(value = "goalId", required = false) Long goalId,
            @PageableDefault(size = 50, sort = "taskDate", direction = Sort.Direction.ASC) Pageable pageable) {
        LocalDate effectiveFrom = from != null ? from : date;
        LocalDate effectiveTo = to != null ? to : date;
        Page<TaskResponse> page = taskService.listTasks(effectiveFrom, effectiveTo, status, goalId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PostMapping
    @Operation(summary = "Tạo task học tập mới")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskService.createTask(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật task học tập")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.updateTask(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa task học tập")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{id}/complete")
    @Operation(summary = "Đánh dấu hoàn thành hoặc hoàn tác trạng thái task (DONE / TODO)")
    public ResponseEntity<ApiResponse<TaskResponse>> toggleComplete(
            @PathVariable Long id,
            @RequestBody(required = false) CompleteTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.toggleComplete(id, request)));
    }
}
