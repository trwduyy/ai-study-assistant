package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.dto.AiJobAcceptedResponse;
import com.aistudy.assistant.ai.dto.AiJobCancelResponse;
import com.aistudy.assistant.ai.dto.AiJobResponse;
import com.aistudy.assistant.ai.dto.AiSummaryResponse;
import com.aistudy.assistant.ai.dto.CreateExplanationRequest;
import com.aistudy.assistant.ai.dto.CreateSummaryRequest;
import com.aistudy.assistant.ai.dto.GenerateAiFlashcardsRequest;
import com.aistudy.assistant.ai.dto.GenerateAiQuizRequest;
import com.aistudy.assistant.ai.dto.GenerateStudyPlanRequest;
import com.aistudy.assistant.ai.entity.AiJob;
import com.aistudy.assistant.ai.entity.AiJobStatus;
import com.aistudy.assistant.ai.entity.AiJobType;
import com.aistudy.assistant.ai.entity.AiSummary;
import com.aistudy.assistant.ai.entity.SummaryLength;
import com.aistudy.assistant.ai.mapper.AiMapper;
import com.aistudy.assistant.ai.repository.AiJobRepository;
import com.aistudy.assistant.ai.repository.AiSummaryRepository;
import com.aistudy.assistant.ai.worker.AiJobCreatedEvent;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.entity.QuestionType;
import com.aistudy.assistant.learning.entity.QuizDifficulty;
import com.aistudy.assistant.learning.entity.Topic;
import com.aistudy.assistant.learning.flashcard.entity.FlashcardDeck;
import com.aistudy.assistant.learning.flashcard.repository.FlashcardDeckRepository;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.TopicRepository;
import com.aistudy.assistant.schedule.entity.Goal;
import com.aistudy.assistant.schedule.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiJobServiceImpl implements AiJobService {

    private final AiJobRepository aiJobRepository;
    private final AiSummaryRepository aiSummaryRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final GoalRepository goalRepository;
    private final AiMapper aiMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public AiJobResponse getJob(String jobId) {
        AiJob job = requireOwnedJob(jobId);
        return aiMapper.toJobResponse(job);
    }

    @Override
    @Transactional
    public AiJobCancelResponse cancelJob(String jobId) {
        AiJob job = requireOwnedJob(jobId);
        if (job.getStatus().isTerminal() && job.getStatus() != AiJobStatus.CANCELLED) {
            throw new AppException(ErrorCode.CONFLICT, "Tác vụ AI đã hoàn tất hoặc kết thúc, không thể hủy.");
        }
        if (job.getStatus() != AiJobStatus.CANCELLED) {
            job.setStatus(AiJobStatus.CANCELLED);
            job.setFinishedAt(Instant.now());
            aiJobRepository.save(job);
        }
        return new AiJobCancelResponse(job.getId(), job.getStatus());
    }

    @Override
    @Transactional
    public AiJobAcceptedResponse requestSummary(CreateSummaryRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Material material = requireExtractedOwnedMaterial(request.materialId(), userId);

        SummaryLength length = request.length() != null ? request.length() : SummaryLength.MEDIUM;
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("materialId", material.getId());
        input.put("length", length.name());

        return queueJob(userId, AiJobType.SUMMARY, input);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiSummaryResponse> listMaterialSummaries(Long materialId) {
        Long userId = SecurityUtils.currentUserId();
        Material material = materialRepository.findByIdAndDeletedAtIsNull(materialId)
                .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_NOT_FOUND));
        if (!material.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return aiSummaryRepository.findProjectedByMaterialIdAndUserId(materialId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public AiSummaryResponse getSummary(Long summaryId) {
        AiSummary summary = requireOwnedSummary(summaryId);
        return aiMapper.toSummaryResponse(summary);
    }

    @Override
    @Transactional
    public void deleteSummary(Long summaryId) {
        AiSummary summary = requireOwnedSummary(summaryId);
        aiSummaryRepository.delete(summary);
    }

    @Override
    @Transactional
    public AiJobAcceptedResponse requestExplanation(CreateExplanationRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Material material = requireExtractedOwnedMaterial(request.materialId(), userId);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("materialId", material.getId());
        input.put("concept", request.concept().trim());
        input.put("contextSnippet", request.contextSnippet() != null ? request.contextSnippet().trim() : null);
        input.put("allowExternalKnowledge", Boolean.TRUE.equals(request.allowExternalKnowledge()));

        return queueJob(userId, AiJobType.EXPLAIN, input);
    }

    @Override
    @Transactional
    public AiJobAcceptedResponse requestQuizGeneration(GenerateAiQuizRequest request) {
        Long userId = SecurityUtils.currentUserId();

        Course course = courseRepository.findByIdAndDeletedAtIsNull(request.courseId())
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        if (request.topicId() != null) {
            Topic topic = topicRepository.findWithChapterCourseAndOwnerById(request.topicId())
                    .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
            if (!topic.getChapter().getCourse().getId().equals(course.getId())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Chủ đề không thuộc khóa học đã chọn.");
            }
        }

        List<Long> distinctMaterialIds = request.materialIds().stream().distinct().toList();
        List<Material> materials = materialRepository.findByIdInAndDeletedAtIsNull(distinctMaterialIds);
        if (materials.size() != distinctMaterialIds.size()) {
            throw new AppException(ErrorCode.MATERIAL_NOT_FOUND);
        }

        Map<Long, Material> byId = materials.stream().collect(Collectors.toMap(Material::getId, m -> m));
        for (Long materialId : distinctMaterialIds) {
            Material material = byId.get(materialId);
            if (!material.getOwner().getId().equals(userId)) {
                throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
            }
            if (!material.getCourse().getId().equals(course.getId())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tài liệu nguồn không thuộc khóa học đã chọn.");
            }
            if (material.getStatus() != MaterialStatus.EXTRACTED
                    || materialChunkRepository.countByMaterialId(materialId) == 0) {
                throw new AppException(ErrorCode.TEXT_NOT_EXTRACTED);
            }
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("courseId", course.getId());
        input.put("materialIds", distinctMaterialIds);
        input.put("topicId", request.topicId());
        input.put("title", request.title().trim());
        input.put("topic", request.topic() != null ? request.topic().trim() : null);
        input.put("questionCount", request.questionCount() != null ? request.questionCount() : 10);
        input.put("difficulty", (request.difficulty() != null ? request.difficulty() : QuizDifficulty.MEDIUM).name());
        input.put("type", (request.type() != null ? request.type() : QuestionType.SINGLE_CHOICE).name());

        return queueJob(userId, AiJobType.QUIZ, input);
    }

    @Override
    @Transactional
    public AiJobAcceptedResponse requestFlashcardGeneration(GenerateAiFlashcardsRequest request) {
        Long userId = SecurityUtils.currentUserId();

        if (request.deckId() != null) {
            FlashcardDeck deck = flashcardDeckRepository.findWithDetailsById(request.deckId())
                    .orElseThrow(() -> new AppException(ErrorCode.FLASHCARD_DECK_NOT_FOUND));
            if (!deck.getOwner().getId().equals(userId)) {
                throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
            }
        }
        if (request.courseId() != null) {
            Course course = courseRepository.findByIdAndDeletedAtIsNull(request.courseId())
                    .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
            if (!course.getOwner().getId().equals(userId)) {
                throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
            }
        }

        List<Long> distinctMaterialIds = request.materialIds().stream().distinct().toList();
        List<Material> materials = materialRepository.findByIdInAndDeletedAtIsNull(distinctMaterialIds);
        if (materials.size() != distinctMaterialIds.size()) {
            throw new AppException(ErrorCode.MATERIAL_NOT_FOUND);
        }
        Map<Long, Material> byId = materials.stream().collect(Collectors.toMap(Material::getId, m -> m));
        for (Long materialId : distinctMaterialIds) {
            Material material = byId.get(materialId);
            if (!material.getOwner().getId().equals(userId)) {
                throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
            }
            if (material.getStatus() != MaterialStatus.EXTRACTED
                    || materialChunkRepository.countByMaterialId(materialId) == 0) {
                throw new AppException(ErrorCode.TEXT_NOT_EXTRACTED);
            }
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("materialIds", distinctMaterialIds);
        input.put("deckId", request.deckId());
        input.put("deckTitle", request.deckTitle() != null ? request.deckTitle().trim() : null);
        input.put("courseId", request.courseId());
        input.put("cardCount", request.cardCount() != null ? request.cardCount() : 10);

        return queueJob(userId, AiJobType.FLASHCARD, input);
    }

    @Override
    @Transactional
    public AiJobAcceptedResponse requestStudyPlan(GenerateStudyPlanRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Goal goal = goalRepository.findById(request.goalId())
                .orElseThrow(() -> new AppException(ErrorCode.GOAL_NOT_FOUND));
        if (!goal.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        java.time.LocalDate fromDate = request.fromDate() != null
                ? request.fromDate()
                : java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        java.time.LocalDate toDate = request.toDate() != null
                ? request.toDate()
                : fromDate.plusDays(6);

        if (toDate.isBefore(fromDate)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Ngày kết thúc (toDate) không được trước ngày bắt đầu (fromDate).");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) > 31) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Khoảng thời gian lập lộ trình tối đa là 31 ngày.");
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("goalId", goal.getId());
        input.put("fromDate", fromDate.toString());
        input.put("toDate", toDate.toString());

        return queueJob(userId, AiJobType.PLAN, input);
    }

    private AiJobAcceptedResponse queueJob(Long userId, AiJobType type, Map<String, Object> input) {
        User user = userRepository.getReferenceById(userId);
        AiJob job = AiJob.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .type(type)
                .status(AiJobStatus.PENDING)
                .inputJson(input)
                .attempts(0)
                .build();
        job = aiJobRepository.save(job);
        eventPublisher.publishEvent(new AiJobCreatedEvent(job.getId()));
        return new AiJobAcceptedResponse(job.getId(), job.getStatus());
    }

    private AiJob requireOwnedJob(String jobId) {
        Long userId = SecurityUtils.currentUserId();
        AiJob job = aiJobRepository.findWithUserById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_JOB_NOT_FOUND));
        if (!job.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return job;
    }

    private AiSummary requireOwnedSummary(Long summaryId) {
        Long userId = SecurityUtils.currentUserId();
        AiSummary summary = aiSummaryRepository.findWithDetailsById(summaryId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_SUMMARY_NOT_FOUND));
        if (!summary.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return summary;
    }

    private Material requireExtractedOwnedMaterial(Long materialId, Long userId) {
        Material material = materialRepository.findByIdAndDeletedAtIsNull(materialId)
                .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_NOT_FOUND));
        if (!material.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (material.getStatus() != MaterialStatus.EXTRACTED
                || materialChunkRepository.countByMaterialId(materialId) == 0) {
            throw new AppException(ErrorCode.TEXT_NOT_EXTRACTED);
        }
        return material;
    }
}
