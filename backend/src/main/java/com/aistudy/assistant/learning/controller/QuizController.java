package com.aistudy.assistant.learning.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.learning.dto.AttemptReviewResponse;
import com.aistudy.assistant.learning.dto.QuizAttemptSummaryResponse;
import com.aistudy.assistant.learning.dto.QuizDetailResponse;
import com.aistudy.assistant.learning.dto.QuizSubmitResultResponse;
import com.aistudy.assistant.learning.dto.QuizSummaryResponse;
import com.aistudy.assistant.learning.dto.SaveAttemptDraftRequest;
import com.aistudy.assistant.learning.dto.SaveAttemptDraftResponse;
import com.aistudy.assistant.learning.dto.StartAttemptResponse;
import com.aistudy.assistant.learning.dto.SubmitAttemptRequest;
import com.aistudy.assistant.learning.dto.UpdateQuizRequest;
import com.aistudy.assistant.learning.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for Quiz V2 management, draft persistence, idempotent submissions, and attempt reviews.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/quizzes")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<ApiResponse<List<QuizSummaryResponse>>> listQuizzes(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<QuizSummaryResponse> result = quizService.listQuizzes(courseId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), PageMeta.ofPage(result)));
    }

    @GetMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> getQuiz(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(quizService.getQuizForAttempt(id)));
    }

    @PutMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<QuizSummaryResponse>> updateQuiz(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuizRequest request) {
        return ResponseEntity.ok(ApiResponse.success(quizService.updateQuiz(id, request)));
    }

    @DeleteMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuiz(@PathVariable Long id) {
        quizService.deleteQuiz(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/quizzes/{id}/attempts")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<StartAttemptResponse>> startAttempt(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(quizService.startAttempt(id)));
    }

    @PutMapping("/quizzes/{id}/attempts/{attemptId}")
    public ResponseEntity<ApiResponse<SaveAttemptDraftResponse>> saveAttemptDraft(
            @PathVariable Long id,
            @PathVariable Long attemptId,
            @Valid @RequestBody SaveAttemptDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.success(quizService.saveAttemptDraft(id, attemptId, request)));
    }

    @PostMapping("/quizzes/{id}/attempts/{attemptId}/submit")
    public ResponseEntity<ApiResponse<QuizSubmitResultResponse>> submitAttempt(
            @PathVariable Long id,
            @PathVariable Long attemptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitAttemptRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                quizService.submitAttempt(id, attemptId, idempotencyKey, request)
        ));
    }

    @GetMapping("/quizzes/{id}/attempts")
    public ResponseEntity<ApiResponse<List<QuizAttemptSummaryResponse>>> listAttempts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        );
        Page<QuizAttemptSummaryResponse> result = quizService.listAttempts(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), PageMeta.ofPage(result)));
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<ApiResponse<AttemptReviewResponse>> getAttemptReview(@PathVariable Long attemptId) {
        return ResponseEntity.ok(ApiResponse.success(quizService.getAttemptReview(attemptId)));
    }
}
