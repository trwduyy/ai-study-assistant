package com.aistudy.assistant.chat.controller;

import com.aistudy.assistant.chat.dto.ChatMessageResponse;
import com.aistudy.assistant.chat.dto.SendMessageRequest;
import com.aistudy.assistant.chat.service.ChatService;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP WebSocket message handler for {@code /app/conversations/{id}/messages}.
 * Persists via {@link ChatService}, broadcasts to {@code /topic/conversations/{id}}, and acks sender.
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;

    @MessageMapping("/conversations/{id}/messages")
    @SendToUser("/queue/messages")
    public ChatMessageResponse handleMessage(
            @DestinationVariable Long id,
            @Valid @Payload SendMessageRequest request,
            Principal principal) {
        Long senderId = extractUserId(principal);
        return chatService.sendMessage(id, senderId, request);
    }

    private Long extractUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        if (principal != null && principal.getName() != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException ignored) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
