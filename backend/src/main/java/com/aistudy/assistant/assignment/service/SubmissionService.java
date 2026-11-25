package com.aistudy.assistant.assignment.service;

import com.aistudy.assistant.assignment.dto.GradeSubmissionRequest;
import com.aistudy.assistant.assignment.dto.ReopenSubmissionRequest;
import com.aistudy.assistant.assignment.dto.SubmissionResponse;
import com.aistudy.assistant.assignment.dto.SubmissionSummaryResponse;
import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SubmissionService {

    SubmissionResponse submitAssignment(
            Long assignmentId,
            String idempotencyKey,
            String textAnswer,
            List<MultipartFile> files
    );

    Page<SubmissionSummaryResponse> getSubmissions(
            Long assignmentId,
            SubmissionStatus status,
            Boolean late,
            Pageable pageable
    );

    SubmissionResponse getMySubmission(Long assignmentId);

    SubmissionResponse gradeSubmission(Long submissionId, GradeSubmissionRequest request, Integer ifMatchVersion);

    SubmissionResponse reopenSubmission(Long submissionId, ReopenSubmissionRequest request, Integer ifMatchVersion);

    SubmissionFileDownload downloadSubmissionFile(Long submissionId, Long fileId);

    record SubmissionFileDownload(String fileName, String mimeType, Resource resource) {
    }
}
