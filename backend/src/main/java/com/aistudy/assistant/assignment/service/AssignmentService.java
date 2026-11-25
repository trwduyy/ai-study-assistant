package com.aistudy.assistant.assignment.service;

import com.aistudy.assistant.assignment.dto.AssignmentResponse;
import com.aistudy.assistant.assignment.dto.AssignmentSummaryResponse;
import com.aistudy.assistant.assignment.dto.CreateAssignmentRequest;
import com.aistudy.assistant.assignment.dto.UpdateAssignmentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AssignmentService {

    AssignmentResponse createAssignment(Long classId, CreateAssignmentRequest request, List<MultipartFile> files);

    Page<AssignmentSummaryResponse> getAssignments(Long classId, Pageable pageable);

    AssignmentResponse getAssignmentDetail(Long assignmentId);

    AssignmentResponse updateAssignment(Long assignmentId, UpdateAssignmentRequest request, Integer ifMatchVersion);

    void deleteAssignment(Long assignmentId);
}
