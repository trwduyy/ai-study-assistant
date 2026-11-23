package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.common.SecurityUtils;
import com.aistudy.assistant.dto.chat.ChatAnswerResponse;
import com.aistudy.assistant.dto.chat.ChatMessageResponse;
import com.aistudy.assistant.dto.chat.ChatSessionResponse;
import com.aistudy.assistant.dto.chat.CreateChatSessionRequest;
import com.aistudy.assistant.dto.chat.SendMessageRequest;
import com.aistudy.assistant.entity.ChatMessage;
import com.aistudy.assistant.entity.ChatRole;
import com.aistudy.assistant.entity.ChatSession;
import com.aistudy.assistant.entity.Course;
import com.aistudy.assistant.entity.ExtractionStatus;
import com.aistudy.assistant.entity.Material;
import com.aistudy.assistant.entity.User;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.repository.ChatMessageRepository;
import com.aistudy.assistant.repository.ChatSessionRepository;
import com.aistudy.assistant.repository.CourseRepository;
import com.aistudy.assistant.repository.MaterialRepository;
import com.aistudy.assistant.repository.UserRepository;
import com.aistudy.assistant.service.ChatService;
import com.aistudy.assistant.service.GeminiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int MAX_CONTEXT_LENGTH = 12000;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CourseRepository courseRepository;
    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final GeminiClientService geminiClientService;

    @Override
    @Transactional
    public ChatSessionResponse createSession(CreateChatSessionRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        Course course = null;
        if (request.getCourseId() != null) {
            course = courseRepository.findByIdAndUserId(request.getCourseId(), currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));
        }

        Material material = null;
        if (request.getMaterialId() != null) {
            material = materialRepository.findByIdAndUserId(request.getMaterialId(), currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Material", "id", request.getMaterialId()));

            if (course == null && material.getCourse() != null) {
                course = material.getCourse();
            }
        }

        ChatSession session = ChatSession.builder()
                .user(currentUser)
                .course(course)
                .material(material)
                .title(request.getTitle().trim())
                .build();

        ChatSession savedSession = chatSessionRepository.save(session);
        log.info("Chat session {} created by user {}", savedSession.getId(), currentUserId);

        return ChatSessionResponse.fromEntity(savedSession);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getUserSessions(Long courseId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<ChatSession> sessions;

        if (courseId != null) {
            sessions = chatSessionRepository.findByUserIdAndCourseIdOrderByUpdatedAtDesc(currentUserId, courseId);
        } else {
            sessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(currentUserId);
        }

        return sessions.stream()
                .map(ChatSessionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(Long sessionId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        ChatSession session = findSessionAndValidateOwnership(sessionId, currentUserId);
        return ChatSessionResponse.fromEntity(session);
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        ChatSession session = findSessionAndValidateOwnership(sessionId, currentUserId);

        chatSessionRepository.delete(session);
        log.info("Chat session {} deleted by user {}", sessionId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getSessionMessages(Long sessionId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        findSessionAndValidateOwnership(sessionId, currentUserId);

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatAnswerResponse sendMessage(Long sessionId, SendMessageRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        ChatSession session = findSessionAndValidateOwnership(sessionId, currentUserId);

        // Fetch recent messages for conversational context before adding new user message
        List<ChatMessage> recentMessages = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        List<ChatMessage> chronologicalHistory = new ArrayList<>(recentMessages);
        Collections.reverse(chronologicalHistory);

        // Save incoming user message
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(ChatRole.USER)
                .content(request.getContent().trim())
                .build();
        ChatMessage savedUserMessage = chatMessageRepository.save(userMessage);

        // Assemble document ground truth context
        String documentContext = resolveDocumentContext(session);

        // Build prompts for Gemini
        String systemPrompt = """
                Bạn là trợ lý gia sư học tập AI thông minh, tận tâm và có tính sư phạm cao.
                Nhiệm vụ của bạn là giải đáp câu hỏi của người học bằng tiếng Việt chuẩn mực, dễ hiểu và truyền cảm hứng.
                Quy tắc quan trọng:
                1. Hãy ưu tiên căn cứ vào nội dung tài liệu học tập được đính kèm để trả lời chính xác nhất.
                2. Nếu tài liệu không chứa câu trả lời, hãy thành thật nêu rõ điều đó, sau đó giải thích theo kiến thức học thuật tổng quát chuẩn xác.
                3. Giải thích từng bước rõ ràng nếu câu hỏi liên quan đến bài tập hoặc suy luận.
                4. Sử dụng công thức toán học/kỹ thuật theo cú pháp LaTeX KaTeX ($...$ hoặc $$...$$) nếu cần thiết.
                """;

        String userPrompt = buildChatPrompt(documentContext, chronologicalHistory, request.getContent().trim());

        // Call Gemini
        log.info("Generating AI response for chat session {} (user question length: {})",
                sessionId, request.getContent().length());
        String aiAnswerText = geminiClientService.generateText(systemPrompt, userPrompt);

        // Save assistant message
        ChatMessage assistantMessage = ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(aiAnswerText)
                .build();
        ChatMessage savedAssistantMessage = chatMessageRepository.save(assistantMessage);

        // Update session timestamp
        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);

        return new ChatAnswerResponse(
                ChatMessageResponse.fromEntity(savedUserMessage),
                ChatMessageResponse.fromEntity(savedAssistantMessage)
        );
    }

    private String resolveDocumentContext(ChatSession session) {
        if (session.getMaterial() != null) {
            Material material = session.getMaterial();
            if (material.getExtractionStatus() == ExtractionStatus.SUCCESS && StringUtils.hasText(material.getExtractedText())) {
                String text = material.getExtractedText();
                if (text.length() > MAX_CONTEXT_LENGTH) {
                    text = text.substring(0, MAX_CONTEXT_LENGTH) + "\n...[Nội dung tài liệu đã được cắt gọn]";
                }
                return String.format("Tài liệu chính: '%s'\n%s", material.getOriginalName(), text);
            }
        } else if (session.getCourse() != null) {
            List<Material> courseMaterials = materialRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(
                    session.getCourse().getId(), session.getUser().getId()
            );

            StringBuilder courseContext = new StringBuilder();
            int currentLength = 0;

            for (Material m : courseMaterials) {
                if (m.getExtractionStatus() == ExtractionStatus.SUCCESS && StringUtils.hasText(m.getExtractedText())) {
                    String part = String.format("\n--- Tài liệu: %s ---\n%s\n", m.getOriginalName(), m.getExtractedText());
                    if (currentLength + part.length() > MAX_CONTEXT_LENGTH) {
                        int remaining = MAX_CONTEXT_LENGTH - currentLength;
                        if (remaining > 500) {
                            courseContext.append(part, 0, remaining).append("\n...[Đã giới hạn ngữ cảnh]");
                        }
                        break;
                    }
                    courseContext.append(part);
                    currentLength += part.length();
                }
            }

            if (courseContext.length() > 0) {
                return courseContext.toString();
            }
        }

        return "Không có tài liệu đính kèm cụ thể cho phiên chat này.";
    }

    private String buildChatPrompt(String documentContext, List<ChatMessage> history, String userQuestion) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== TÀI LIỆU HỌC TẬP THAM KHẢO ===\n");
        sb.append(documentContext).append("\n\n");

        if (history != null && !history.isEmpty()) {
            sb.append("=== LỊCH SỬ HỘI THOẠI GẦN ĐÂY ===\n");
            for (ChatMessage msg : history) {
                String roleName = msg.getRole() == ChatRole.USER ? "Học sinh" : "Trợ lý AI";
                sb.append(roleName).append(": ").append(msg.getContent()).append("\n\n");
            }
        }

        sb.append("=== CÂU HỎI MỚI CỦA HỌC SINH ===\n");
        sb.append(userQuestion);

        return sb.toString();
    }

    private ChatSession findSessionAndValidateOwnership(Long sessionId, Long userId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession", "id", sessionId));
    }
}
