package com.aistudy.assistant.ai.controller;

import com.aistudy.assistant.ai.dto.AiChatAnswerResponse;
import com.aistudy.assistant.ai.dto.AiChatDetailResponse;
import com.aistudy.assistant.ai.dto.AiChatResponse;
import com.aistudy.assistant.ai.dto.AiFeedbackResponse;
import com.aistudy.assistant.ai.dto.AiJobAcceptedResponse;
import com.aistudy.assistant.ai.dto.AiJobCancelResponse;
import com.aistudy.assistant.ai.dto.AiJobResponse;
import com.aistudy.assistant.ai.dto.AiSummaryResponse;
import com.aistudy.assistant.ai.dto.CreateAiChatRequest;
import com.aistudy.assistant.ai.dto.CreateAiFeedbackRequest;
import com.aistudy.assistant.ai.dto.CreateExplanationRequest;
import com.aistudy.assistant.ai.dto.CreateSummaryRequest;
import com.aistudy.assistant.ai.dto.GenerateAiFlashcardsRequest;
import com.aistudy.assistant.ai.dto.GenerateAiQuizRequest;
import com.aistudy.assistant.ai.dto.SendAiChatMessageRequest;
import com.aistudy.assistant.ai.service.AiChatService;
import com.aistudy.assistant.ai.service.AiChatService.ChatDetailSlice;
import com.aistudy.assistant.ai.service.AiFeedbackService;
import com.aistudy.assistant.ai.service.AiJobService;
import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for asynchronous AI jobs, summaries, concept explanations, grounded Q&A chat, quiz generation, and feedback.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiController {

    private final AiJobService aiJobService;
    private final AiChatService aiChatService;
    private final AiFeedbackService aiFeedbackService;

    @GetMapping("/ai/jobs/{jobId}")
    public ResponseEntity<ApiResponse<AiJobResponse>> getJob(@PathVariable String jobId) {
        return ResponseEntity.ok(ApiResponse.success(aiJobService.getJob(jobId)));
    }

    @DeleteMapping("/ai/jobs/{jobId}")
    public ResponseEntity<ApiResponse<AiJobCancelResponse>> cancelJob(@PathVariable String jobId) {
        return ResponseEntity.ok(ApiResponse.success(aiJobService.cancelJob(jobId)));
    }

    @PostMapping("/ai/summaries")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> createSummary(
            @Valid @RequestBody CreateSummaryRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(aiJobService.requestSummary(request)));
    }

    @GetMapping("/materials/{materialId}/summaries")
    public ResponseEntity<ApiResponse<List<AiSummaryResponse>>> listMaterialSummaries(
            @PathVariable Long materialId) {
        return ResponseEntity.ok(ApiResponse.success(aiJobService.listMaterialSummaries(materialId)));
    }

    @GetMapping("/ai/summaries/{id}")
    public ResponseEntity<ApiResponse<AiSummaryResponse>> getSummary(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(aiJobService.getSummary(id)));
    }

    @DeleteMapping("/ai/summaries/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSummary(@PathVariable Long id) {
        aiJobService.deleteSummary(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/ai/explanations")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> createExplanation(
            @Valid @RequestBody CreateExplanationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(aiJobService.requestExplanation(request)));
    }

    @PostMapping("/ai/quizzes")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> generateQuiz(
            @Valid @RequestBody GenerateAiQuizRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(aiJobService.requestQuizGeneration(request)));
    }

    @PostMapping("/ai/flashcards")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AiJobAcceptedResponse>> generateFlashcards(
            @Valid @RequestBody GenerateAiFlashcardsRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(aiJobService.requestFlashcardGeneration(request)));
    }

    @PostMapping("/ai/chats")
    public ResponseEntity<ApiResponse<AiChatResponse>> createChat(
            @Valid @RequestBody CreateAiChatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiChatService.createChat(request)));
    }

    @GetMapping("/ai/chats")
    public ResponseEntity<ApiResponse<List<AiChatResponse>>> listChats(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<AiChatResponse> chatPage = aiChatService.listChats(courseId, pageable);
        return ResponseEntity.ok(ApiResponse.success(chatPage.getContent(), PageMeta.ofPage(chatPage)));
    }

    @GetMapping("/ai/chats/{chatId}")
    public ResponseEntity<ApiResponse<AiChatDetailResponse>> getChatDetail(
            @PathVariable Long chatId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int limit) {
        ChatDetailSlice slice = aiChatService.getChatDetail(chatId, cursor, limit);
        String nextCursorStr = slice.nextCursor() != null ? String.valueOf(slice.nextCursor()) : null;
        return ResponseEntity.ok(ApiResponse.success(
                slice.detail(),
                PageMeta.ofCursor(slice.size(), nextCursorStr)
        ));
    }

    @DeleteMapping("/ai/chats/{chatId}")
    public ResponseEntity<ApiResponse<Void>> deleteChat(@PathVariable Long chatId) {
        aiChatService.deleteChat(chatId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/ai/chats/{chatId}/messages")
    public ResponseEntity<ApiResponse<AiChatAnswerResponse>> sendMessage(
            @PathVariable Long chatId,
            @Valid @RequestBody SendAiChatMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiChatService.sendMessage(chatId, request)));
    }

    @PostMapping("/ai/feedback")
    public ResponseEntity<ApiResponse<AiFeedbackResponse>> submitFeedback(
            @Valid @RequestBody CreateAiFeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiFeedbackService.submitFeedback(request)));
    }
}
