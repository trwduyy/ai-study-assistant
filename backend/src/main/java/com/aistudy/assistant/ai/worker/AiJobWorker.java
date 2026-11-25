package com.aistudy.assistant.ai.worker;

import com.aistudy.assistant.ai.entity.AiJob;
import com.aistudy.assistant.ai.entity.AiJobStatus;
import com.aistudy.assistant.ai.entity.SummaryLength;
import com.aistudy.assistant.ai.integration.GeminiClientService;
import com.aistudy.assistant.ai.repository.AiJobRepository;
import com.aistudy.assistant.ai.service.AiQuizValidator;
import com.aistudy.assistant.ai.service.AiQuizValidator.GeneratedQuestion;
import com.aistudy.assistant.ai.service.ChunkGroundingService;
import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;
import com.aistudy.assistant.ai.worker.AiJobTxService.ClaimedJobSnapshot;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.learning.entity.QuestionType;
import com.aistudy.assistant.learning.entity.QuizDifficulty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asynchronous background worker executing {@code ai_jobs}.
 * Never holds database transactions open while waiting on external Gemini requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiJobWorker {

    public static final String PROMPT_VERSION_SUMMARY = "summary-v2.0";
    public static final String PROMPT_VERSION_EXPLAIN = "explain-v2.0";
    public static final String PROMPT_VERSION_QUIZ = "quiz-v2.0";
    private static final String NOT_ENOUGH_INFO_MESSAGE = "Tài liệu hiện tại chưa có thông tin để kết luận";
    private static final int MAX_CONTEXT_CHARS = 14000;
    private static final int MAX_QUIZ_VALIDATION_RETRIES = 2;

    private final AiJobRepository aiJobRepository;
    private final AiJobTxService aiJobTxService;
    private final GeminiClientService geminiClientService;
    private final ChunkGroundingService chunkGroundingService;
    private final AiQuizValidator aiQuizValidator;
    private final ObjectMapper objectMapper;

    @Async("aiJobExecutor")
    public void processJobAsync(String jobId) {
        processJob(jobId);
    }

    @Scheduled(fixedDelayString = "${app.ai.worker-poll-ms:3000}")
    public void pollPendingJobs() {
        List<AiJob> pendingJobs = aiJobRepository.findTop20ByStatusOrderByCreatedAtAsc(AiJobStatus.PENDING);
        for (AiJob pending : pendingJobs) {
            processJobAsync(pending.getId());
        }
    }

    public void processJob(String jobId) {
        Optional<ClaimedJobSnapshot> claimedOpt = aiJobTxService.claimPendingJob(jobId);
        if (claimedOpt.isEmpty()) {
            return;
        }
        ClaimedJobSnapshot job = claimedOpt.get();

        try {
            switch (job.type()) {
                case SUMMARY -> handleSummaryJob(job);
                case EXPLAIN -> handleExplainJob(job);
                case QUIZ -> handleQuizJob(job);
                default -> aiJobTxService.failJob(
                        job.jobId(),
                        ErrorCode.UNPROCESSABLE_ENTITY.name(),
                        "Loại tác vụ AI '" + job.type() + "' chưa được hỗ trợ ở giai đoạn hiện tại."
                );
            }
        } catch (AppException ex) {
            log.warn("AI job {} failed with code {}: {}", job.jobId(), ex.getErrorCode().name(), ex.getMessage());
            aiJobTxService.failJob(job.jobId(), ex.getErrorCode().name(), ex.getMessage());
        } catch (Exception ex) {
            log.error("AI job {} failed unexpectedly: {}", job.jobId(), ex.getMessage(), ex);
            aiJobTxService.failJob(
                    job.jobId(),
                    ErrorCode.INTERNAL_ERROR.name(),
                    "Xử lý tác vụ AI thất bại: " + ex.getMessage()
            );
        }
    }

    private void handleSummaryJob(ClaimedJobSnapshot job) {
        Long materialId = asLong(job.inputJson().get("materialId"));
        if (materialId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu materialId trong tác vụ tóm tắt.");
        }
        SummaryLength length = parseEnum(job.inputJson().get("length"), SummaryLength.class, SummaryLength.MEDIUM);

        List<ChunkCandidate> chunks = aiJobTxService.loadChunks(List.of(materialId));
        if (chunks.isEmpty()) {
            throw new AppException(ErrorCode.TEXT_NOT_EXTRACTED);
        }

        String contextText = buildContextText(chunks);
        String lengthInstruction = switch (length) {
            case SHORT -> "Tóm tắt súc tích (150-250 từ) bằng các gạch đầu dòng nêu các ý cốt lõi nhất.";
            case DETAILED -> "Tóm tắt chi tiết có phân chia theo từng đề mục Markdown rõ ràng, giữ lại các định nghĩa và công thức quan trọng.";
            case MEDIUM -> "Tóm tắt mức trung bình (300-500 từ) theo cấu trúc Markdown gồm: Khái niệm cốt lõi, Nội dung chính, và Điểm cần lưu ý.";
        };

        String systemPrompt = """
                Bạn là trợ lý học tập AI. Hãy tóm tắt tài liệu hoàn toàn bằng tiếng Việt phổ thông rõ ràng, chính xác,
                chỉ dựa trên nội dung tài liệu được cung cấp và không tự bịa thêm thông tin ngoài tài liệu.
                Định dạng đầu ra bằng Markdown chuẩn.
                """;
        String userPrompt = lengthInstruction + "\n\nNội dung tài liệu:\n" + contextText;

        String summaryContent = geminiClientService.generateText(systemPrompt, userPrompt);
        if (!StringUtils.hasText(summaryContent)) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "AI trả về bản tóm tắt rỗng.");
        }

        aiJobTxService.completeSummaryJob(
                job.jobId(),
                materialId,
                length,
                summaryContent.trim(),
                geminiClientService.getModel(),
                PROMPT_VERSION_SUMMARY
        );
    }

    private void handleExplainJob(ClaimedJobSnapshot job) {
        Long materialId = asLong(job.inputJson().get("materialId"));
        String concept = asString(job.inputJson().get("concept"));
        String contextSnippet = asString(job.inputJson().get("contextSnippet"));
        boolean allowExternal = asBoolean(job.inputJson().get("allowExternalKnowledge"));

        if (materialId == null || !StringUtils.hasText(concept)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu thông tin khái niệm hoặc tài liệu.");
        }

        List<ChunkCandidate> allChunks = aiJobTxService.loadChunks(List.of(materialId));
        if (allChunks.isEmpty()) {
            throw new AppException(ErrorCode.TEXT_NOT_EXTRACTED);
        }

        String query = StringUtils.hasText(contextSnippet) ? concept + " " + contextSnippet : concept;
        List<ChunkCandidate> matched = chunkGroundingService.selectTopChunks(query, allChunks, 5);

        if (matched.isEmpty() && !allowExternal) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("concept", concept);
            output.put("explanation", NOT_ENOUGH_INFO_MESSAGE);
            output.put("grounded", false);
            output.put("sources", List.of());
            output.put("aiAddedNote", "Bạn có thể bật tùy chọn tham khảo kiến thức ngoài để AI giải thích mở rộng.");
            output.put("model", geminiClientService.getModel());
            output.put("promptVersion", PROMPT_VERSION_EXPLAIN);
            aiJobTxService.completeExplainJob(job.jobId(), materialId, output);
            return;
        }

        List<ChunkCandidate> promptChunks = matched.isEmpty()
                ? allChunks.stream().limit(3).toList()
                : matched;

        String systemPrompt = """
                Bạn là trợ lý học tập AI. Giải thích khái niệm bằng tiếng Việt phổ thông dễ hiểu.
                Trả về đúng định dạng JSON theo schema yêu cầu:
                - explanation: giải thích bám sát đoạn tài liệu được cung cấp. Nếu tài liệu không đề cập và allowExternalKnowledge=false thì đặt explanation = "Tài liệu hiện tại chưa có thông tin để kết luận" và grounded = false.
                - aiAddedNote: nếu allowExternalKnowledge=true và có kiến thức mở rộng ngoài tài liệu, hãy tách riêng hoàn toàn vào trường aiAddedNote bắt đầu bằng "Phần giải thích thêm của AI: ".
                - citedChunkIds: danh sách chunkId thực sự đã trích dẫn.
                """;

        String userPrompt = String.format(
                "Khái niệm cần giải thích: %s\nNgữ cảnh người dùng chọn: %s\nallowExternalKnowledge: %s\n\nCác đoạn trích tài liệu:\n%s",
                concept,
                StringUtils.hasText(contextSnippet) ? contextSnippet : "(Không có)",
                allowExternal,
                formatGroundedChunks(promptChunks)
        );

        String rawJson = geminiClientService.generateStructuredJson(systemPrompt, userPrompt, buildExplainSchema());
        Map<String, Object> parsedOutput = parseExplainOutput(rawJson, concept, promptChunks, allowExternal);
        aiJobTxService.completeExplainJob(job.jobId(), materialId, parsedOutput);
    }

    private void handleQuizJob(ClaimedJobSnapshot job) {
        Long courseId = asLong(job.inputJson().get("courseId"));
        List<Long> materialIds = asLongList(job.inputJson().get("materialIds"));
        Long topicId = asLong(job.inputJson().get("topicId"));
        String title = asString(job.inputJson().get("title"));
        String topic = asString(job.inputJson().get("topic"));
        Integer questionCount = asInteger(job.inputJson().get("questionCount"), 10);
        QuizDifficulty difficulty = parseEnum(job.inputJson().get("difficulty"), QuizDifficulty.class, QuizDifficulty.MEDIUM);
        QuestionType questionType = parseEnum(job.inputJson().get("type"), QuestionType.class, QuestionType.SINGLE_CHOICE);

        List<ChunkCandidate> allChunks = aiJobTxService.loadChunks(materialIds);
        if (allChunks.isEmpty()) {
            throw new AppException(ErrorCode.TEXT_NOT_EXTRACTED);
        }

        List<ChunkCandidate> selectedChunks = StringUtils.hasText(topic)
                ? chunkGroundingService.selectTopChunks(topic, allChunks, 10)
                : List.of();
        if (selectedChunks.isEmpty()) {
            selectedChunks = allChunks;
        }

        String contextText = formatChunksWithIds(selectedChunks);
        Set<Long> allowedChunkIds = selectedChunks.stream()
                .map(ChunkCandidate::chunkId)
                .collect(Collectors.toSet());

        String typeRule = questionType == QuestionType.SINGLE_CHOICE
                ? "Mỗi câu hỏi bắt buộc có ĐÚNG 4 đáp án (A, B, C, D) và CHỈ CÓ ĐÚNG 1 đáp án có isCorrect=true."
                : "Mỗi câu hỏi có 4 đáp án (A, B, C, D) và có ít nhất 1 đáp án có isCorrect=true.";

        String systemPrompt = """
                Bạn là chuyên gia ra đề kiểm tra học thuật. Hãy sinh bộ câu hỏi trắc nghiệm hoàn toàn bằng tiếng Việt phổ thông,
                chỉ bám sát nội dung trong các đoạn tài liệu được cung cấp.
                Yêu cầu bắt buộc:
                1. %s
                2. Mỗi câu hỏi phải có trường explanation giải thích rõ lý do đáp án đúng dựa trên tài liệu.
                3. Gắn sourceChunkId tương ứng với chunkId chứa thông tin ra câu hỏi.
                4. Trả về đúng JSON hợp lệ theo responseSchema, không thêm văn bản ngoài JSON.
                """.formatted(typeRule);

        String userPrompt = String.format(
                "Tiêu đề quiz: %s\nChủ đề trọng tâm: %s\nSố lượng câu hỏi cần tạo: %d\nĐộ khó: %s\nLoại câu hỏi: %s\n\nNội dung tài liệu tham chiếu:\n%s",
                title,
                StringUtils.hasText(topic) ? topic : "Tổng hợp nội dung tài liệu",
                questionCount,
                difficulty.name(),
                questionType.name(),
                contextText
        );

        Map<String, Object> schema = aiQuizValidator.buildQuizJsonSchema();
        List<GeneratedQuestion> validatedQuestions = null;
        AppException lastValidationError = null;

        for (int attempt = 0; attempt <= MAX_QUIZ_VALIDATION_RETRIES; attempt++) {
            try {
                String prompt = attempt == 0
                        ? userPrompt
                        : userPrompt + "\n\nLƯU Ý SỬA LỖI ĐỊNH DẠNG LẦN TRƯỚC: " + lastValidationError.getMessage();
                String rawJson = geminiClientService.generateStructuredJson(systemPrompt, prompt, schema);
                validatedQuestions = aiQuizValidator.parseAndValidate(rawJson, questionType, allowedChunkIds);
                break;
            } catch (AppException ex) {
                if (ex.getErrorCode() == ErrorCode.AI_OUTPUT_INVALID && attempt < MAX_QUIZ_VALIDATION_RETRIES) {
                    lastValidationError = ex;
                    log.warn("Quiz JSON validation failed for job {} (attempt {}/{}): {}",
                            job.jobId(), attempt + 1, MAX_QUIZ_VALIDATION_RETRIES + 1, ex.getMessage());
                    continue;
                }
                throw ex;
            }
        }

        if (validatedQuestions == null || validatedQuestions.isEmpty()) {
            throw lastValidationError != null
                    ? lastValidationError
                    : new AppException(ErrorCode.AI_OUTPUT_INVALID);
        }

        aiJobTxService.completeQuizJob(
                job.jobId(),
                courseId,
                materialIds,
                topicId,
                StringUtils.hasText(title) ? title : "Quiz ôn tập AI",
                difficulty,
                questionType,
                validatedQuestions,
                geminiClientService.getModel(),
                PROMPT_VERSION_QUIZ
        );
    }

    private Map<String, Object> parseExplainOutput(
            String rawJson,
            String concept,
            List<ChunkCandidate> promptChunks,
            boolean allowExternal) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            boolean grounded = root.path("grounded").asBoolean(true);
            String explanation = root.path("explanation").asText("").trim();
            String aiAddedNote = root.has("aiAddedNote") && !root.get("aiAddedNote").isNull()
                    ? root.get("aiAddedNote").asText("").trim()
                    : null;

            if (!StringUtils.hasText(explanation)) {
                explanation = NOT_ENOUGH_INFO_MESSAGE;
                grounded = false;
            }
            if (!grounded && !allowExternal) {
                explanation = NOT_ENOUGH_INFO_MESSAGE;
            }

            Map<Long, ChunkCandidate> byId = promptChunks.stream()
                    .collect(Collectors.toMap(ChunkCandidate::chunkId, c -> c, (a, b) -> a));

            List<Map<String, Object>> sources = new ArrayList<>();
            if (grounded) {
                JsonNode cited = root.path("citedChunkIds");
                if (cited.isArray()) {
                    for (JsonNode idNode : cited) {
                        if (idNode.canConvertToLong()) {
                            ChunkCandidate candidate = byId.get(idNode.asLong());
                            if (candidate != null) {
                                sources.add(toSourceMap(candidate));
                            }
                        }
                    }
                }
                if (sources.isEmpty() && !promptChunks.isEmpty()) {
                    sources.add(toSourceMap(promptChunks.get(0)));
                }
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("concept", concept);
            output.put("explanation", explanation);
            output.put("grounded", grounded);
            output.put("sources", sources);
            output.put("aiAddedNote", StringUtils.hasText(aiAddedNote) ? aiAddedNote : null);
            output.put("model", geminiClientService.getModel());
            output.put("promptVersion", PROMPT_VERSION_EXPLAIN);
            return output;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Không thể phân tích kết quả giải thích từ AI.");
        }
    }

    private Map<String, Object> toSourceMap(ChunkCandidate candidate) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("materialId", candidate.materialId());
        source.put("fileName", candidate.fileName());
        source.put("page", candidate.pageFrom());
        source.put("chunkId", candidate.chunkId());
        return source;
    }

    private Map<String, Object> buildExplainSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("grounded", Map.of("type", "BOOLEAN"));
        properties.put("explanation", Map.of("type", "STRING"));
        properties.put("aiAddedNote", Map.of("type", "STRING"));
        properties.put("citedChunkIds", Map.of("type", "ARRAY", "items", Map.of("type", "INTEGER")));

        return Map.of(
                "type", "OBJECT",
                "properties", properties,
                "required", List.of("grounded", "explanation")
        );
    }

    private String buildContextText(List<ChunkCandidate> chunks) {
        StringBuilder sb = new StringBuilder();
        for (ChunkCandidate chunk : chunks) {
            String pageLabel = chunk.pageFrom() != null ? " (Trang " + chunk.pageFrom() + ")" : "";
            String block = "[Phần " + (chunk.chunkIndex() + 1) + pageLabel + "]\n" + chunk.content() + "\n\n";
            if (sb.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            sb.append(block);
        }
        return sb.toString();
    }

    private String formatGroundedChunks(List<ChunkCandidate> chunks) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    private String formatChunksWithIds(List<ChunkCandidate> chunks) {
        StringBuilder sb = new StringBuilder();
        for (ChunkCandidate chunk : chunks) {
            String block = String.format(
                    "[chunkId=%d | page=%s]\n%s\n\n",
                    chunk.chunkId(),
                    chunk.pageFrom() != null ? chunk.pageFrom() : "N/A",
                    chunk.content()
            );
            if (sb.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            sb.append(block);
        }
        return sb.toString();
    }

    private Long asLong(Object val) {
        if (val instanceof Number number) {
            return number.longValue();
        }
        if (val instanceof String str && StringUtils.hasText(str)) {
            return Long.parseLong(str.trim());
        }
        return null;
    }

    private Integer asInteger(Object val, int defaultValue) {
        if (val instanceof Number number) {
            return number.intValue();
        }
        if (val instanceof String str && StringUtils.hasText(str)) {
            return Integer.parseInt(str.trim());
        }
        return defaultValue;
    }

    private String asString(Object val) {
        return val != null ? String.valueOf(val) : null;
    }

    private boolean asBoolean(Object val) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    private List<Long> asLongList(Object val) {
        if (!(val instanceof List<?> list)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            Long id = asLong(item);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private <E extends Enum<E>> E parseEnum(Object val, Class<E> enumType, E fallback) {
        if (val == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, String.valueOf(val).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
