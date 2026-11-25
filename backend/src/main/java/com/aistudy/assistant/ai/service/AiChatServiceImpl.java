package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.dto.AiChatAnswerResponse;
import com.aistudy.assistant.ai.dto.AiChatDetailResponse;
import com.aistudy.assistant.ai.dto.AiChatMessageDto;
import com.aistudy.assistant.ai.dto.AiChatResponse;
import com.aistudy.assistant.ai.dto.CreateAiChatRequest;
import com.aistudy.assistant.ai.dto.SendAiChatMessageRequest;
import com.aistudy.assistant.ai.entity.AiChat;
import com.aistudy.assistant.ai.entity.AiChatMessage;
import com.aistudy.assistant.ai.entity.AiMessageRole;
import com.aistudy.assistant.ai.entity.AiMessageSource;
import com.aistudy.assistant.ai.integration.GeminiClientService;
import com.aistudy.assistant.ai.mapper.AiMapper;
import com.aistudy.assistant.ai.repository.AiChatMessageRepository;
import com.aistudy.assistant.ai.repository.AiChatRepository;
import com.aistudy.assistant.ai.repository.AiMessageSourceRepository;
import com.aistudy.assistant.ai.service.AiChatTxService.ChatHistoryTurn;
import com.aistudy.assistant.ai.service.AiChatTxService.PreparedChatTurn;
import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    public static final String NOT_ENOUGH_INFO_ANSWER = "Tài liệu hiện tại chưa có thông tin để kết luận";
    public static final String PROMPT_VERSION_CHAT = "grounded-chat-v2.0";
    private static final int TOP_K_CHUNKS = 5;

    private final AiChatRepository aiChatRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiMessageSourceRepository aiMessageSourceRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final AiChatTxService aiChatTxService;
    private final ChunkGroundingService chunkGroundingService;
    private final GeminiClientService geminiClientService;
    private final AiMapper aiMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AiChatResponse createChat(CreateAiChatRequest request) {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.getReferenceById(userId);

        Course course = null;
        if (request.courseId() != null) {
            course = courseRepository.findByIdAndDeletedAtIsNull(request.courseId())
                    .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
            if (!course.getOwner().getId().equals(userId)) {
                throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
            }
        }

        AiChat chat = AiChat.builder()
                .user(user)
                .course(course)
                .title(request.title().trim())
                .build();
        chat = aiChatRepository.save(chat);
        return aiMapper.toChatResponse(chat);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AiChatResponse> listChats(Long courseId, Pageable pageable) {
        Long userId = SecurityUtils.currentUserId();
        return aiChatRepository.findProjectedByUserId(userId, courseId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDetailSlice getChatDetail(Long chatId, Long cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        AiChat chat = requireOwnedChat(chatId);

        List<AiChatMessage> fetched = aiChatMessageRepository.findRecentMessages(
                chatId,
                cursor,
                PageRequest.of(0, safeLimit + 1)
        );
        boolean hasMore = fetched.size() > safeLimit;
        List<AiChatMessage> pageSlice = hasMore
                ? new ArrayList<>(fetched.subList(0, safeLimit))
                : new ArrayList<>(fetched);
        Long nextCursor = hasMore ? pageSlice.get(pageSlice.size() - 1).getId() : null;

        Collections.reverse(pageSlice);

        Map<Long, List<AiMessageSource>> sourcesByMessageId = Map.of();
        if (!pageSlice.isEmpty()) {
            List<Long> messageIds = pageSlice.stream().map(AiChatMessage::getId).toList();
            sourcesByMessageId = aiMessageSourceRepository.findWithMaterialByMessageIdIn(messageIds).stream()
                    .collect(Collectors.groupingBy(ms -> ms.getMessage().getId()));
        }

        Map<Long, List<AiMessageSource>> finalSourcesMap = sourcesByMessageId;
        List<AiChatMessageDto> messageDtos = pageSlice.stream()
                .map(msg -> aiMapper.toMessageDto(msg, finalSourcesMap.getOrDefault(msg.getId(), List.of())))
                .toList();

        return new ChatDetailSlice(
                new AiChatDetailResponse(
                        chat.getId(),
                        chat.getCourse() != null ? chat.getCourse().getId() : null,
                        chat.getTitle(),
                        messageDtos
                ),
                safeLimit,
                nextCursor
        );
    }

    @Override
    @Transactional
    public void deleteChat(Long chatId) {
        AiChat chat = requireOwnedChat(chatId);
        aiChatRepository.delete(chat);
    }

    @Override
    public AiChatAnswerResponse sendMessage(Long chatId, SendAiChatMessageRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String question = request.content().trim();
        boolean allowExternal = Boolean.TRUE.equals(request.allowExternalKnowledge());

        PreparedChatTurn prepared = aiChatTxService.prepareAndSaveUserTurn(
                chatId,
                userId,
                question,
                request.materialIds()
        );

        List<ChunkCandidate> topChunks = chunkGroundingService.selectTopChunks(
                question,
                prepared.candidateChunks(),
                TOP_K_CHUNKS
        );

        if (topChunks.isEmpty() && !allowExternal) {
            return aiChatTxService.saveAssistantTurn(
                    chatId,
                    NOT_ENOUGH_INFO_ANSWER,
                    false,
                    List.of(),
                    "Bạn có thể bật tùy chọn tham khảo kiến thức ngoài để AI giải thích mở rộng.",
                    geminiClientService.getModel(),
                    PROMPT_VERSION_CHAT
            );
        }

        List<ChunkCandidate> promptChunks = topChunks.isEmpty()
                ? prepared.candidateChunks().stream().limit(3).toList()
                : topChunks;

        String rawJson = geminiClientService.generateStructuredJson(
                buildChatSystemPrompt(allowExternal),
                buildChatUserPrompt(question, allowExternal, prepared.recentHistory(), promptChunks),
                buildChatResponseSchema()
        );

        ParsedChatOutput parsed = parseChatResponse(rawJson, promptChunks, allowExternal);
        return aiChatTxService.saveAssistantTurn(
                chatId,
                parsed.answer(),
                parsed.grounded(),
                parsed.citedChunks(),
                parsed.aiAddedNote(),
                geminiClientService.getModel(),
                PROMPT_VERSION_CHAT
        );
    }

    private ParsedChatOutput parseChatResponse(
            String rawJson,
            List<ChunkCandidate> promptChunks,
            boolean allowExternal) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            boolean grounded = root.path("grounded").asBoolean(!promptChunks.isEmpty());
            String answer = root.path("answer").asText("").trim();
            String aiAddedNote = root.has("aiAddedNote") && !root.get("aiAddedNote").isNull()
                    ? root.get("aiAddedNote").asText("").trim()
                    : null;

            if (!StringUtils.hasText(answer)) {
                throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "AI trả về câu trả lời rỗng.");
            }

            if (!grounded && !allowExternal) {
                answer = NOT_ENOUGH_INFO_ANSWER;
            }

            List<ChunkCandidate> citedChunks = new ArrayList<>();
            if (grounded && !promptChunks.isEmpty()) {
                Map<Long, ChunkCandidate> byId = promptChunks.stream()
                        .collect(Collectors.toMap(ChunkCandidate::chunkId, c -> c, (a, b) -> a));
                Set<Long> selectedIds = new LinkedHashSet<>();

                JsonNode citedNode = root.path("citedChunkIds");
                if (citedNode.isArray()) {
                    for (JsonNode item : citedNode) {
                        if (item.canConvertToLong() && byId.containsKey(item.asLong())) {
                            selectedIds.add(item.asLong());
                        }
                    }
                }
                if (selectedIds.isEmpty()) {
                    selectedIds.add(promptChunks.get(0).chunkId());
                }
                for (Long id : selectedIds) {
                    citedChunks.add(byId.get(id));
                }
            }

            if (citedChunks.isEmpty() && !allowExternal) {
                grounded = false;
                answer = NOT_ENOUGH_INFO_ANSWER;
            }

            if (!StringUtils.hasText(aiAddedNote)) {
                aiAddedNote = null;
            }

            return new ParsedChatOutput(answer, grounded, citedChunks, aiAddedNote);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to parse grounded chat JSON: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Không thể đọc phản hồi JSON từ dịch vụ AI.");
        }
    }

    private String buildChatSystemPrompt(boolean allowExternal) {
        String externalRule = allowExternal
                ? """
                  Khi allowExternalKnowledge=true, nếu cần giải thích mở rộng từ kiến thức bên ngoài tài liệu,
                  hãy đặt toàn bộ phần mở rộng đó vào riêng trường `aiAddedNote` với tiền tố "Phần giải thích thêm của AI: ".
                  """
                : """
                  Khi allowExternalKnowledge=false, chỉ được kết luận dựa trên tài liệu được cung cấp.
                  Nếu tài liệu không chứa thông tin trả lời câu hỏi, bắt buộc đặt grounded=false, citedChunkIds=[]
                  và answer="Tài liệu hiện tại chưa có thông tin để kết luận".
                  """;

        return """
                Bạn là trợ lý hỏi đáp học tập AI trả lời hoàn toàn bằng tiếng Việt phổ thông.
                Quy tắc bắt buộc:
                1. Luôn trả về JSON hợp lệ theo schema.
                2. Trường `answer` chỉ chứa nội dung được chứng minh từ các đoạn tài liệu đính kèm.
                3. Liệt kê chính xác `chunkId` đã dùng trong mảng `citedChunkIds`.
                4. %s
                """.formatted(externalRule);
    }

    private String buildChatUserPrompt(
            String question,
            boolean allowExternal,
            List<ChatHistoryTurn> history,
            List<ChunkCandidate> chunks) {
        StringBuilder sb = new StringBuilder();
        if (!history.isEmpty()) {
            sb.append("Lịch sử hội thoại gần nhất:\n");
            for (ChatHistoryTurn turn : history) {
                sb.append(turn.role() == AiMessageRole.USER ? "Người học: " : "AI: ")
                        .append(turn.content())
                        .append("\n");
            }
            sb.append("\n");
        }

        sb.append("allowExternalKnowledge: ").append(allowExternal).append("\n");
        sb.append("Câu hỏi hiện tại: ").append(question).append("\n\n");
        sb.append("Các đoạn tài liệu trích xuất liên quan:\n");
        if (chunks.isEmpty()) {
            sb.append("(Không tìm thấy đoạn tài liệu phù hợp)\n");
        } else {
            for (ChunkCandidate chunk : chunks) {
                sb.append(String.format(
                        "[chunkId=%d | materialId=%d | file=%s | page=%s]\n%s\n\n",
                        chunk.chunkId(),
                        chunk.materialId(),
                        chunk.fileName(),
                        chunk.pageFrom() != null ? chunk.pageFrom() : "N/A",
                        chunk.content()
                ));
            }
        }
        return sb.toString();
    }

    private Map<String, Object> buildChatResponseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("grounded", Map.of("type", "BOOLEAN"));
        properties.put("answer", Map.of("type", "STRING"));
        properties.put("aiAddedNote", Map.of("type", "STRING"));
        properties.put("citedChunkIds", Map.of("type", "ARRAY", "items", Map.of("type", "INTEGER")));

        return Map.of(
                "type", "OBJECT",
                "properties", properties,
                "required", List.of("grounded", "answer", "citedChunkIds")
        );
    }

    private AiChat requireOwnedChat(Long chatId) {
        Long userId = SecurityUtils.currentUserId();
        AiChat chat = aiChatRepository.findWithDetailsById(chatId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_CHAT_NOT_FOUND));
        if (!chat.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return chat;
    }

    private record ParsedChatOutput(
            String answer,
            boolean grounded,
            List<ChunkCandidate> citedChunks,
            String aiAddedNote
    ) {
    }
}
