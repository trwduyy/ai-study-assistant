package com.aistudy.assistant.assignment.service;

import com.aistudy.assistant.assignment.dto.ClassTestResponse;
import com.aistudy.assistant.assignment.dto.ClassTestResultsResponse;
import com.aistudy.assistant.assignment.dto.ClassTestSubmitResultResponse;
import com.aistudy.assistant.assignment.dto.CreateClassTestRequest;
import com.aistudy.assistant.assignment.dto.StartClassTestAttemptResponse;
import com.aistudy.assistant.assignment.dto.UpdateClassTestRequest;
import com.aistudy.assistant.learning.dto.SubmitAttemptRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClassTestService {

    ClassTestResponse createTest(Long classId, CreateClassTestRequest request);

    Page<ClassTestResponse> getTests(Long classId, Pageable pageable);

    ClassTestResponse updateTest(Long testId, UpdateClassTestRequest request);

    void deleteTest(Long testId);

    StartClassTestAttemptResponse startAttempt(Long testId);

    ClassTestSubmitResultResponse submitAttempt(
            Long testId,
            Long attemptId,
            String idempotencyKey,
            SubmitAttemptRequest request
    );

    ClassTestResultsResponse getResults(Long testId);
}
