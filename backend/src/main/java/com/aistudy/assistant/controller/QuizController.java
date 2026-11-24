package com.aistudy.assistant.controller;

import com.aistudy.assistant.common.ApiResponse;
import com.aistudy.assistant.dto.quiz.AttemptDetailResponse;
import com.aistudy.assistant.dto.quiz.GenerateQuizRequest;
import com.aistudy.assistant.dto.quiz.QuizAttemptHistoryResponse;
import com.aistudy.assistant.dto.quiz.QuizExamResponse;
import com.aistudy.assistant.dto.quiz.QuizResponse;
import com.aistudy.assistant.dto.quiz.QuizSubmitResultResponse;
import com.aistudy.assistant.dto.quiz.SubmitQuizRequest;
import com.aistudy.assistant.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "AI Quiz & Evaluation", description = "Endpoints for generating AI quizzes, taking exams, auto-scoring, and reviewing attempts")
@SecurityRequirement(name = "BearerAuth")
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/quizzes/generate")
    @Operation(summary = "Generate a new AI quiz with multiple-choice questions from course or material")
    public ResponseEntity<ApiResponse<QuizResponse>> generateQuiz(
            @Valid @RequestBody GenerateQuizRequest request) {
        QuizResponse response = quizService.generateQuiz(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Quiz generated successfully"));
    }

    @GetMapping("/quizzes")
    @Operation(summary = "List all quizzes belonging to a specific course")
    public ResponseEntity<ApiResponse<List<QuizResponse>>> getQuizzesByCourse(
            @RequestParam("courseId") Long courseId) {
        List<QuizResponse> quizzes = quizService.getQuizzesByCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success(quizzes, "Quizzes retrieved successfully"));
    }

    @GetMapping("/quizzes/{id}")
    @Operation(summary = "Get quiz details for exam taking (answers and explanations are hidden)")
    public ResponseEntity<ApiResponse<QuizExamResponse>> getQuizForExam(@PathVariable Long id) {
        QuizExamResponse response = quizService.getQuizExamDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Quiz loaded for examination"));
    }

    @PostMapping("/quizzes/{id}/attempts")
    @Operation(summary = "Submit quiz answers, perform automatic scoring, and record attempt")
    public ResponseEntity<ApiResponse<QuizSubmitResultResponse>> submitQuiz(
            @PathVariable Long id,
            @Valid @RequestBody SubmitQuizRequest request) {
        QuizSubmitResultResponse response = quizService.submitQuiz(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quiz submitted and evaluated successfully"));
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    @Operation(summary = "Get comprehensive quiz attempt review including user choices, correct answers, and explanations")
    public ResponseEntity<ApiResponse<AttemptDetailResponse>> getAttemptDetail(
            @PathVariable Long attemptId) {
        AttemptDetailResponse response = quizService.getAttemptDetail(attemptId);
        return ResponseEntity.ok(ApiResponse.success(response, "Attempt details and explanations retrieved"));
    }

    @GetMapping("/quizzes/{id}/attempts")
    @Operation(summary = "Get historical attempts of a specific quiz")
    public ResponseEntity<ApiResponse<List<QuizAttemptHistoryResponse>>> getQuizAttempts(
            @PathVariable Long id) {
        List<QuizAttemptHistoryResponse> attempts = quizService.getQuizAttempts(id);
        return ResponseEntity.ok(ApiResponse.success(attempts, "Quiz attempt history retrieved"));
    }
}
