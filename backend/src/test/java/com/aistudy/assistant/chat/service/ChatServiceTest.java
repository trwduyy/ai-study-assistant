package com.aistudy.assistant.chat.service;

import com.aistudy.assistant.chat.dto.ChatMessageResponse;
import com.aistudy.assistant.chat.dto.ConversationResponse;
import com.aistudy.assistant.chat.dto.CreateDirectConversationRequest;
import com.aistudy.assistant.chat.dto.SendMessageRequest;
import com.aistudy.assistant.chat.entity.Conversation;
import com.aistudy.assistant.chat.entity.ConversationMember;
import com.aistudy.assistant.chat.entity.ConversationType;
import com.aistudy.assistant.chat.entity.Message;
import com.aistudy.assistant.chat.entity.MessageStatus;
import com.aistudy.assistant.chat.mapper.ChatMapper;
import com.aistudy.assistant.chat.repository.ConversationMemberRepository;
import com.aistudy.assistant.chat.repository.ConversationRepository;
import com.aistudy.assistant.chat.repository.MessageReadStateRepository;
import com.aistudy.assistant.chat.repository.MessageRepository;
import com.aistudy.assistant.chat.service.impl.ChatServiceImpl;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReadStateRepository messageReadStateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatServiceImpl chatService;

    private User userA;
    private User userB;
    private Conversation classConversation;

    @BeforeEach
    void setUp() {
        TransactionTemplate immediateTxTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }

            @Override
            public void executeWithoutResult(Consumer<TransactionStatus> action) {
                action.accept(new SimpleTransactionStatus());
            }
        };

        chatService = new ChatServiceImpl(
                conversationRepository,
                conversationMemberRepository,
                messageRepository,
                messageReadStateRepository,
                userRepository,
                new ChatMapper(),
                messagingTemplate,
                immediateTxTemplate
        );

        userA = User.builder()
                .id(5L)
                .email("a@actvn.edu.vn")
                .fullName("Nguyễn Văn A")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        userB = User.builder()
                .id(12L)
                .email("b@actvn.edu.vn")
                .fullName("Trần Thị B")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();

        Classroom classroom = Classroom.builder()
                .id(100L)
                .code("KMA7N9P4")
                .name("Mạng máy tính")
                .build();

        classConversation = Conversation.builder()
                .id(15L)
                .type(ConversationType.CLASSROOM)
                .classroom(classroom)
                .updatedAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("openOrGetDirectConversation generates identical directKey 5:12 regardless of who initiates")
    void openOrGetDirectConversation_SymmetricDirectKey() {
        // User 5 initiates with User 12
        authenticateAs(userA);
        when(userRepository.findById(5L)).thenReturn(Optional.of(userA));
        when(userRepository.findById(12L)).thenReturn(Optional.of(userB));
        when(conversationRepository.findByDirectKey("5:12")).thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(22L);
            c.setUpdatedAt(Instant.now());
            return c;
        });
        when(messageRepository.findLatestMessagesByConversationIds(List.of(22L))).thenReturn(List.of());
        when(messageRepository.countUnreadByConversationIds(5L, List.of(22L))).thenReturn(List.of());

        ConversationResponse createdByA = chatService.openOrGetDirectConversation(new CreateDirectConversationRequest(12L));

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).saveAndFlush(convCaptor.capture());
        assertEquals("5:12", convCaptor.getValue().getDirectKey());
        assertEquals(22L, createdByA.id());

        // User 12 opens direct chat with User 5 -> finds same "5:12" conversation
        authenticateAs(userB);
        Conversation existingConv = Conversation.builder()
                .id(22L)
                .type(ConversationType.DIRECT)
                .directKey("5:12")
                .updatedAt(Instant.now())
                .build();
        when(conversationRepository.findByDirectKey("5:12")).thenReturn(Optional.of(existingConv));
        when(messageRepository.countUnreadByConversationIds(12L, List.of(22L))).thenReturn(List.of());

        ConversationResponse fetchedByB = chatService.openOrGetDirectConversation(new CreateDirectConversationRequest(5L));
        assertEquals(22L, fetchedByB.id());
        verify(conversationMemberRepository, org.mockito.Mockito.times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("sendMessage with duplicate clientMessageId returns existing message without creating second row")
    void sendMessage_DuplicateClientMessageId_ReturnsExistingWithoutDuplicateSave() {
        ConversationMember activeMember = ConversationMember.builder()
                .id(1L)
                .conversation(classConversation)
                .user(userA)
                .joinedAt(Instant.now())
                .leftAt(null)
                .build();

        Message existingMessage = Message.builder()
                .id(501L)
                .conversation(classConversation)
                .sender(userA)
                .content("Chào cả lớp")
                .status(MessageStatus.SENT)
                .clientMessageId("uuid-1234")
                .createdAt(Instant.now())
                .build();

        when(conversationRepository.findById(15L)).thenReturn(Optional.of(classConversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(15L, 5L))
                .thenReturn(Optional.of(activeMember));
        when(messageRepository.findWithDetailsByConversationIdAndSenderIdAndClientMessageId(15L, 5L, "uuid-1234"))
                .thenReturn(Optional.of(existingMessage));

        ChatMessageResponse result = chatService.sendMessage(
                15L,
                5L,
                new SendMessageRequest("uuid-1234", "Chào cả lớp")
        );

        assertEquals(501L, result.id());
        assertEquals("uuid-1234", result.clientMessageId());
        verify(messageRepository, never()).saveAndFlush(any());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/conversations/15"), any(Object.class));
    }

    @Test
    @DisplayName("Removed member (leftAt != null) gets 403 NOT_CONVERSATION_MEMBER when reading or sending")
    void removedMember_ReadOrSend_ThrowsForbidden() {
        authenticateAs(userA);
        ConversationMember removedMember = ConversationMember.builder()
                .id(1L)
                .conversation(classConversation)
                .user(userA)
                .joinedAt(Instant.now().minusSeconds(3600))
                .leftAt(Instant.now().minusSeconds(60))
                .build();

        when(conversationRepository.findById(15L)).thenReturn(Optional.of(classConversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(15L, 5L))
                .thenReturn(Optional.of(removedMember));

        AppException readEx = assertThrows(
                AppException.class,
                () -> chatService.getMessages(15L, null, 30)
        );
        assertEquals(ErrorCode.NOT_CONVERSATION_MEMBER, readEx.getErrorCode());

        AppException sendEx = assertThrows(
                AppException.class,
                () -> chatService.sendMessage(15L, 5L, new SendMessageRequest("uuid-new", "Tin nhắn"))
        );
        assertEquals(ErrorCode.NOT_CONVERSATION_MEMBER, sendEx.getErrorCode());
        verify(messageRepository, never()).findProjectedByConversationIdWithCursor(any(), any(), any(PageRequest.class));
        verify(messageRepository, never()).saveAndFlush(any());
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
