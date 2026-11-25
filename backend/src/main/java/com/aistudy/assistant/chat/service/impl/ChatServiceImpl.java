package com.aistudy.assistant.chat.service.impl;

import com.aistudy.assistant.chat.dto.ChatMessageResponse;
import com.aistudy.assistant.chat.dto.ConversationReadStateResponse;
import com.aistudy.assistant.chat.dto.ConversationResponse;
import com.aistudy.assistant.chat.dto.CreateDirectConversationRequest;
import com.aistudy.assistant.chat.dto.MarkConversationReadRequest;
import com.aistudy.assistant.chat.dto.SendMessageRequest;
import com.aistudy.assistant.chat.entity.Conversation;
import com.aistudy.assistant.chat.entity.ConversationMember;
import com.aistudy.assistant.chat.entity.ConversationType;
import com.aistudy.assistant.chat.entity.Message;
import com.aistudy.assistant.chat.entity.MessageReadState;
import com.aistudy.assistant.chat.entity.MessageStatus;
import com.aistudy.assistant.chat.mapper.ChatMapper;
import com.aistudy.assistant.chat.repository.ConversationMemberRepository;
import com.aistudy.assistant.chat.repository.ConversationRepository;
import com.aistudy.assistant.chat.repository.ConversationRepository.ActiveConversationRow;
import com.aistudy.assistant.chat.repository.MessageReadStateRepository;
import com.aistudy.assistant.chat.repository.MessageRepository;
import com.aistudy.assistant.chat.repository.MessageRepository.ConversationUnreadCount;
import com.aistudy.assistant.chat.service.ChatService;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business service for real-time 1-1 and classroom group chat (FR-18), enforcing active membership,
 * idempotent message delivery, cursor pagination, and monotonic read watermarks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int DEFAULT_MESSAGE_LIMIT = 30;
    private static final int MAX_MESSAGE_LIMIT = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final MessageReadStateRepository messageReadStateRepository;
    private final UserRepository userRepository;
    private final ChatMapper chatMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactionTemplate;

    public static String buildDirectKey(Long userA, Long userB) {
        long minId = Math.min(userA, userB);
        long maxId = Math.max(userA, userB);
        return minId + ":" + maxId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations() {
        Long currentUserId = SecurityUtils.currentUserId();
        List<ActiveConversationRow> rows = conversationRepository.findActiveConversationRows(currentUserId);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> conversationIds = rows.stream().map(ActiveConversationRow::getConversationId).toList();
        Map<Long, Message> lastMessageByConv = messageRepository
                .findLatestMessagesByConversationIds(conversationIds)
                .stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity(), (a, b) -> a));

        Map<Long, Long> unreadByConv = messageRepository
                .countUnreadByConversationIds(currentUserId, conversationIds)
                .stream()
                .collect(Collectors.toMap(
                        ConversationUnreadCount::getConversationId,
                        row -> row.getUnreadCount() != null ? row.getUnreadCount() : 0L
                ));

        return rows.stream()
                .map(row -> {
                    Long convId = row.getConversationId();
                    Message lastMsg = lastMessageByConv.get(convId);
                    String title = row.getType() == ConversationType.CLASSROOM
                            ? row.getClassName()
                            : row.getOtherUserName();
                    Instant sortTime = lastMsg != null ? lastMsg.getCreatedAt() : row.getUpdatedAt();
                    return new ConversationResponse(
                            convId,
                            row.getType(),
                            row.getClassId(),
                            title != null ? title : "Cuộc trò chuyện",
                            unreadByConv.getOrDefault(convId, 0L),
                            chatMapper.toLastMessageSummary(lastMsg),
                            sortTime
                    );
                })
                .sorted(Comparator.comparing(
                        ConversationResponse::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(ConversationResponse::id, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public ConversationResponse openOrGetDirectConversation(CreateDirectConversationRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Long targetUserId = request.userId();
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể tạo hội thoại 1-1 với chính mình.");
        }

        String directKey = buildDirectKey(currentUserId, targetUserId);

        // Fast path inside read-only transaction
        ConversationResponse existing = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            if (targetUser.getStatus() != UserStatus.ACTIVE) {
                throw new AppException(ErrorCode.UNPROCESSABLE_ENTITY, "Tài khoản người nhận hiện đang bị khóa.");
            }
            return conversationRepository.findByDirectKey(directKey)
                    .map(conv -> buildDirectConversationResponse(conv, currentUserId, targetUser))
                    .orElse(null);
        });
        if (existing != null) {
            return existing;
        }

        try {
            return transactionTemplate.execute(status -> {
                User currentUser = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                User targetUser = userRepository.findById(targetUserId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                Optional<Conversation> raceExisting = conversationRepository.findByDirectKey(directKey);
                if (raceExisting.isPresent()) {
                    return buildDirectConversationResponse(raceExisting.get(), currentUserId, targetUser);
                }

                Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder()
                        .type(ConversationType.DIRECT)
                        .directKey(directKey)
                        .build());

                Instant now = Instant.now();
                conversationMemberRepository.saveAll(List.of(
                        ConversationMember.builder()
                                .conversation(conversation)
                                .user(currentUser)
                                .joinedAt(now)
                                .build(),
                        ConversationMember.builder()
                                .conversation(conversation)
                                .user(targetUser)
                                .joinedAt(now)
                                .build()
                ));

                return buildDirectConversationResponse(conversation, currentUserId, targetUser);
            });
        } catch (DataIntegrityViolationException ex) {
            return transactionTemplate.execute(status -> {
                status.setRollbackOnly();
                User targetUser = userRepository.findById(targetUserId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                Conversation conv = conversationRepository.findByDirectKey(directKey)
                        .orElseThrow(() -> ex);
                return buildDirectConversationResponse(conv, currentUserId, targetUser);
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CursorMessagePage getMessages(Long conversationId, Long cursor, int limit) {
        Long currentUserId = SecurityUtils.currentUserId();
        requireActiveConversationMember(conversationId, currentUserId);

        int safeLimit = limit <= 0 ? DEFAULT_MESSAGE_LIMIT : Math.min(limit, MAX_MESSAGE_LIMIT);
        List<ChatMessageResponse> rows = messageRepository.findProjectedByConversationIdWithCursor(
                conversationId,
                cursor,
                PageRequest.of(0, safeLimit + 1)
        );

        boolean hasMore = rows.size() > safeLimit;
        List<ChatMessageResponse> items = hasMore ? rows.subList(0, safeLimit) : rows;
        String nextCursor = hasMore && !items.isEmpty()
                ? String.valueOf(items.get(items.size() - 1).id())
                : null;
        return new CursorMessagePage(items, nextCursor);
    }

    @Override
    public ChatMessageResponse sendMessage(Long conversationId, Long senderId, SendMessageRequest request) {
        String clientMessageId = request.clientMessageId().trim();
        String content = request.content().trim();

        SendOutcome outcome = transactionTemplate.execute(status -> {
            Conversation conversation = requireActiveConversationMember(conversationId, senderId);

            Optional<Message> existingOpt = messageRepository
                    .findWithDetailsByConversationIdAndSenderIdAndClientMessageId(
                            conversationId, senderId, clientMessageId);
            if (existingOpt.isPresent()) {
                return new SendOutcome(chatMapper.toChatMessageResponse(existingOpt.get()), true);
            }

            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            Message saved;
            try {
                saved = messageRepository.saveAndFlush(Message.builder()
                        .conversation(conversation)
                        .sender(sender)
                        .content(content)
                        .status(MessageStatus.SENT)
                        .clientMessageId(clientMessageId)
                        .build());
            } catch (DataIntegrityViolationException ex) {
                Message duplicate = messageRepository
                        .findWithDetailsByConversationIdAndSenderIdAndClientMessageId(
                                conversationId, senderId, clientMessageId)
                        .orElseThrow(() -> ex);
                return new SendOutcome(chatMapper.toChatMessageResponse(duplicate), true);
            }

            conversation.setUpdatedAt(Instant.now());
            conversationRepository.save(conversation);
            return new SendOutcome(chatMapper.toChatMessageResponse(saved), false);
        });

        if (outcome != null && !outcome.duplicate()) {
            broadcastMessage(conversationId, outcome.response());
        }
        return outcome != null ? outcome.response() : null;
    }

    @Override
    @Transactional
    public ConversationReadStateResponse markAsRead(Long conversationId, MarkConversationReadRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Conversation conversation = requireActiveConversationMember(conversationId, currentUserId);

        if (!messageRepository.existsByIdAndConversationId(request.lastReadMessageId(), conversationId)) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        MessageReadState state = messageReadStateRepository
                .findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseGet(() -> MessageReadState.builder()
                        .conversation(conversation)
                        .user(userRepository.getReferenceById(currentUserId))
                        .build());

        Long currentWatermark = state.getLastReadMessageId();
        if (currentWatermark == null || request.lastReadMessageId() > currentWatermark) {
            state.setLastReadMessageId(request.lastReadMessageId());
            state = messageReadStateRepository.save(state);
        }

        return new ConversationReadStateResponse(
                conversationId,
                state.getLastReadMessageId(),
                state.getUpdatedAt()
        );
    }

    private Conversation requireActiveConversationMember(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_CONVERSATION_MEMBER));
        if (!member.isActive()) {
            throw new AppException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
        return conversation;
    }

    private ConversationResponse buildDirectConversationResponse(
            Conversation conversation,
            Long currentUserId,
            User targetUser) {
        List<Message> latest = messageRepository.findLatestMessagesByConversationIds(List.of(conversation.getId()));
        Message lastMsg = latest.isEmpty() ? null : latest.get(0);
        List<ConversationUnreadCount> unreads = messageRepository.countUnreadByConversationIds(
                currentUserId, List.of(conversation.getId()));
        long unreadCount = unreads.isEmpty() || unreads.get(0).getUnreadCount() == null
                ? 0L
                : unreads.get(0).getUnreadCount();
        Instant sortTime = lastMsg != null ? lastMsg.getCreatedAt() : conversation.getUpdatedAt();

        return new ConversationResponse(
                conversation.getId(),
                ConversationType.DIRECT,
                null,
                targetUser.getFullName(),
                unreadCount,
                chatMapper.toLastMessageSummary(lastMsg),
                sortTime
        );
    }

    private void broadcastMessage(Long conversationId, ChatMessageResponse response) {
        try {
            messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);
        } catch (Exception ex) {
            log.warn("Failed to broadcast chat messageId={} to /topic/conversations/{}: {}",
                    response.id(), conversationId, ex.getMessage());
        }
    }

    private record SendOutcome(ChatMessageResponse response, boolean duplicate) {
    }
}
