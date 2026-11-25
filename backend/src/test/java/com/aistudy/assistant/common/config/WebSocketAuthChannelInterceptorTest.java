package com.aistudy.assistant.common.config;

import com.aistudy.assistant.chat.repository.ConversationMemberRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.security.CustomUserDetailsService;
import com.aistudy.assistant.identity.security.JwtTokenProvider;
import com.aistudy.assistant.identity.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private CustomUserDetailsService customUserDetailsService;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private MessageChannel channel;

    private WebSocketAuthChannelInterceptor interceptor;
    private UserPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(
                jwtTokenProvider,
                customUserDetailsService,
                conversationMemberRepository
        );
        User user = User.builder()
                .id(10L)
                .email("sv@actvn.edu.vn")
                .fullName("SV")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        studentPrincipal = UserPrincipal.create(user);
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE and SEND to conversation destinations reject non-members with NOT_CONVERSATION_MEMBER")
    void subscribeAndSend_NonMember_ThrowsNotConversationMember() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                studentPrincipal, null, studentPrincipal.getAuthorities());

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setDestination("/topic/conversations/15");
        subAccessor.setUser(auth);
        Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        when(conversationMemberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(15L, 10L))
                .thenReturn(false);

        AppException subEx = assertThrows(AppException.class, () -> interceptor.preSend(subMsg, channel));
        assertEquals(ErrorCode.NOT_CONVERSATION_MEMBER, subEx.getErrorCode());

        StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        sendAccessor.setDestination("/app/conversations/15/messages");
        sendAccessor.setUser(auth);
        Message<byte[]> sendMsg = MessageBuilder.createMessage(new byte[0], sendAccessor.getMessageHeaders());

        AppException sendEx = assertThrows(AppException.class, () -> interceptor.preSend(sendMsg, channel));
        assertEquals(ErrorCode.NOT_CONVERSATION_MEMBER, sendEx.getErrorCode());
    }
}
