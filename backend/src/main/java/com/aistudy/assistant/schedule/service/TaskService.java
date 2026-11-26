package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.schedule.dto.CompleteTaskRequest;
import com.aistudy.assistant.schedule.dto.CreateTaskRequest;
import com.aistudy.assistant.schedule.dto.TaskResponse;
import com.aistudy.assistant.schedule.dto.UpdateTaskRequest;
import com.aistudy.assistant.schedule.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface TaskService {

    Page<TaskResponse> listTasks(LocalDate from, LocalDate to, TaskStatus status, Long goalId, Pageable pageable);

    TaskResponse createTask(CreateTaskRequest request);

    TaskResponse updateTask(Long taskId, UpdateTaskRequest request);

    void deleteTask(Long taskId);

    TaskResponse toggleComplete(Long taskId, CompleteTaskRequest request);
}
