package com.aistudy.assistant.chat.controller;

import com.aistudy.assistant.chat.dto.ChatMessageResponse;
import com.aistudy.assistant.chat.dto.ConversationReadStateResponse;
import com.aistudy.assistant.chat.dto.ConversationResponse;
import com.aistudy.assistant.chat.dto.CreateDirectConversationRequest;
import com.aistudy.assistant.chat.dto.MarkConversationReadRequest;
import com.aistudy.assistant.chat.dto.SendMessageRequest;
import com.aistudy.assistant.chat.service.ChatService;
import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.identity.security.SecurityUtils;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for 1-1 and classroom conversations, cursor message history, REST send fallback, and read states (FR-18).
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Chat V2", description = "Phòng chat lớp học và hội thoại 1-1")
@SecurityRequirement(name = "BearerAuth")
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    @Operation(summary = "Danh sách cuộc trò chuyện của người dùng")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getMyConversations() {
        return ResponseEntity.ok(ApiResponse.success(chatService.getMyConversations()));
    }

    @PostMapping("/direct")
    @Operation(summary = "Mở hoặc lấy cuộc trò chuyện 1-1 theo userId")
    public ResponseEntity<ApiResponse<ConversationResponse>> openOrGetDirectConversation(
            @Valid @RequestBody CreateDirectConversationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chatService.openOrGetDirectConversation(request)));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Lấy lịch sử tin nhắn phân trang theo cursor")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @PathVariable Long id,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        ChatService.CursorMessagePage page = chatService.getMessages(id, cursor, limit);
        return ResponseEntity.ok(
                ApiResponse.success(page.items(), PageMeta.ofCursor(page.items().size(), page.nextCursor()))
        );
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Gửi tin nhắn qua REST (dự phòng khi mất WebSocket)")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request) {
        Long senderId = SecurityUtils.currentUserId();
        ChatMessageResponse response = chatService.sendMessage(id, senderId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu đã đọc đến mốc lastReadMessageId")
    public ResponseEntity<ApiResponse<ConversationReadStateResponse>> markAsRead(
            @PathVariable Long id,
            @Valid @RequestBody MarkConversationReadRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chatService.markAsRead(id, request)));
    }
}
