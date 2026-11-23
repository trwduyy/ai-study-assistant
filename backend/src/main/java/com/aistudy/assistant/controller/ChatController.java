package com.aistudy.assistant.controller;

import com.aistudy.assistant.common.ApiResponse;
import com.aistudy.assistant.dto.chat.ChatAnswerResponse;
import com.aistudy.assistant.dto.chat.ChatMessageResponse;
import com.aistudy.assistant.dto.chat.ChatSessionResponse;
import com.aistudy.assistant.dto.chat.CreateChatSessionRequest;
import com.aistudy.assistant.dto.chat.SendMessageRequest;
import com.aistudy.assistant.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "AI Study Chat", description = "Endpoints for document-grounded AI conversational assistance")
@SecurityRequirement(name = "BearerAuth")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/sessions")
    @Operation(summary = "Create a new AI chat study session")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(
            @Valid @RequestBody CreateChatSessionRequest request) {
        ChatSessionResponse response = chatService.createSession(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Chat session created"));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List chat sessions with optional courseId filter")
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> getUserSessions(
            @RequestParam(value = "courseId", required = false) Long courseId) {
        List<ChatSessionResponse> sessions = chatService.getUserSessions(courseId);
        return ResponseEntity.ok(ApiResponse.success(sessions, "Chat sessions retrieved"));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Get chat session by ID")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getSession(@PathVariable Long sessionId) {
        ChatSessionResponse session = chatService.getSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(session, "Chat session details retrieved"));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Delete a chat session and all messages within it")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null, "Chat session deleted successfully"));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "Get all messages within a chat session")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getSessionMessages(
            @PathVariable Long sessionId) {
        List<ChatMessageResponse> messages = chatService.getSessionMessages(sessionId);
        return ResponseEntity.ok(ApiResponse.success(messages, "Messages retrieved"));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "Send a message and receive an AI-generated grounded answer")
    public ResponseEntity<ApiResponse<ChatAnswerResponse>> sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        ChatAnswerResponse response = chatService.sendMessage(sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Message answered successfully"));
    }
}
