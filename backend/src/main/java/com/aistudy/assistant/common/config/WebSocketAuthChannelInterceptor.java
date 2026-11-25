package com.aistudy.assistant.common.config;

import com.aistudy.assistant.chat.repository.ConversationMemberRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.security.CustomUserDetailsService;
import com.aistudy.assistant.identity.security.JwtTokenProvider;
import com.aistudy.assistant.identity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts STOMP frames: authenticates Bearer JWT on {@code CONNECT}, and enforces active conversation
 * membership ({@code left_at IS NULL}) on {@code SUBSCRIBE} and {@code SEND} for conversation destinations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern TOPIC_CONVERSATION_PATTERN = Pattern.compile("^/topic/conversations/(\\d+)$");
    private static final Pattern APP_CONVERSATION_PATTERN = Pattern.compile("^/app/conversations/(\\d+)/messages$");

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final ConversationMemberRepository conversationMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticateConnect(accessor);
            case SUBSCRIBE -> authorizeDestination(accessor, TOPIC_CONVERSATION_PATTERN);
            case SEND -> authorizeDestination(accessor, APP_CONVERSATION_PATTERN);
            default -> {
            }
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String rawAuth = resolveAuthorizationHeader(accessor);
        if (!StringUtils.hasText(rawAuth) || !rawAuth.startsWith(BEARER_PREFIX)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Thiếu token xác thực WebSocket.");
        }

        String jwt = rawAuth.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(jwt) || !jwtTokenProvider.validateToken(jwt)) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        Long userId = jwtTokenProvider.getUserIdFromJwt(jwt);
        String email = jwtTokenProvider.getEmailFromJwt(jwt);
        UserPrincipal principal = userId != null
                ? customUserDetailsService.loadUserById(userId)
                : (UserPrincipal) customUserDetailsService.loadUserByUsername(email);

        if (!principal.isAccountNonLocked() || !principal.isEnabled()) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        String principalName = String.valueOf(principal.getId());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        ) {
            @Override
            public String getName() {
                return principalName;
            }
        };

        accessor.setUser(authentication);
        log.debug("Authenticated WebSocket STOMP session for userId={}", principalName);
    }

    private void authorizeDestination(StompHeaderAccessor accessor, Pattern destinationPattern) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            return;
        }
        Matcher matcher = destinationPattern.matcher(destination.trim());
        if (!matcher.matches()) {
            return;
        }

        Long conversationId = Long.parseLong(matcher.group(1));
        Long userId = extractUserId(accessor.getUser());
        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId);
        if (!isMember) {
            log.warn("Rejected STOMP {} to {} for non-member userId={}",
                    accessor.getCommand(), destination, userId);
            throw new AppException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
    }

    private Long extractUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        if (principal != null && StringUtils.hasText(principal.getName())) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException ignored) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private String resolveAuthorizationHeader(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        return header;
    }
}
