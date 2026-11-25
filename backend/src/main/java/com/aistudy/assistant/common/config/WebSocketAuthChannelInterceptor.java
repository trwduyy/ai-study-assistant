package com.aistudy.assistant.common.config;

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

/**
 * Intercepts STOMP {@code CONNECT} frames, validates the Bearer JWT via {@link JwtTokenProvider},
 * and binds an authenticated {@link Authentication} whose {@code getName()} is the user's numeric ID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

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
        return message;
    }

    private String resolveAuthorizationHeader(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        return header;
    }
}
