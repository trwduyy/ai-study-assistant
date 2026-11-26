package com.aistudy.assistant.ai.worker;

import com.aistudy.assistant.ai.dto.AiJobResponse;
import com.aistudy.assistant.ai.entity.AiJob;
import com.aistudy.assistant.ai.entity.AiJobStatus;
import com.aistudy.assistant.ai.entity.AiJobType;
import com.aistudy.assistant.ai.entity.AiSummary;
import com.aistudy.assistant.ai.entity.SummaryLength;
import com.aistudy.assistant.ai.mapper.AiMapper;
import com.aistudy.assistant.ai.repository.AiJobRepository;
import com.aistudy.assistant.ai.repository.AiSummaryRepository;
import com.aistudy.assistant.ai.service.AiFlashcardValidator;
import com.aistudy.assistant.ai.service.AiQuizValidator;
import com.aistudy.assistant.ai.service.AiStudyPlanValidator;
import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;
import com.aistudy.assistant.assignment.repository.AssignmentRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository.ActiveClassRoleProjection;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse.DueAssignmentSummary;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialChunk;
import com.aistudy.assistant.learning.entity.Question;
import com.aistudy.assistant.learning.entity.QuestionOption;
import com.aistudy.assistant.learning.entity.QuestionType;
import com.aistudy.assistant.learning.entity.Quiz;
import com.aistudy.assistant.learning.entity.QuizDifficulty;
import com.aistudy.assistant.learning.entity.QuizMaterial;
import com.aistudy.assistant.learning.entity.QuizMaterialId;
import com.aistudy.assistant.learning.entity.Topic;
import com.aistudy.assistant.learning.flashcard.entity.Flashcard;
import com.aistudy.assistant.learning.flashcard.entity.FlashcardDeck;
import com.aistudy.assistant.learning.flashcard.repository.FlashcardDeckRepository;
import com.aistudy.assistant.learning.flashcard.repository.FlashcardRepository;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository.TopicPerformanceProjection;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.QuestionOptionRepository;
import com.aistudy.assistant.learning.repository.QuestionRepository;
import com.aistudy.assistant.learning.repository.QuizMaterialRepository;
import com.aistudy.assistant.learning.repository.QuizRepository;
import com.aistudy.assistant.learning.repository.TopicRepository;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.Goal;
import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.StudyPlan;
import com.aistudy.assistant.schedule.entity.StudyPlanItem;
import com.aistudy.assistant.schedule.entity.StudyPlanItemDecision;
import com.aistudy.assistant.schedule.entity.StudyPlanStatus;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.repository.GoalRepository;
import com.aistudy.assistant.schedule.repository.StudyPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Short-lived transactional persistence helper for {@link AiJobWorker}.
 * Isolates database reads and writes from slow external Gemini calls.
 */
@Service
@RequiredArgsConstructor
public class AiJobTxService {

    private final AiJobRepository aiJobRepository;
    private final AiSummaryRepository aiSummaryRepository;
    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final QuizRepository quizRepository;
    private final QuizMaterialRepository quizMaterialRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final FlashcardRepository flashcardRepository;
    private final GoalRepository goalRepository;
    private final ClassMemberRepository classMemberRepository;
    private final EventRepository eventRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final AiMapper aiMapper;

    public record ClaimedJobSnapshot(
            String jobId,
            Long userId,
            AiJobType type,
            Map<String, Object> inputJson
    ) {
    }

    @Transactional
    public Optional<ClaimedJobSnapshot> claimPendingJob(String jobId) {
        int claimed = aiJobRepository.claimPendingJob(
                jobId,
                AiJobStatus.PENDING,
                AiJobStatus.RUNNING,
                Instant.now()
        );
        if (claimed == 0) {
            return Optional.empty();
        }
        return aiJobRepository.findWithUserById(jobId)
                .map(job -> new ClaimedJobSnapshot(
                        job.getId(),
                        job.getUser().getId(),
                        job.getType(),
                        job.getInputJson() != null ? new HashMap<>(job.getInputJson()) : new HashMap<>()
                ));
    }

    @Transactional(readOnly = true)
    public List<ChunkCandidate> loadChunks(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return List.of();
        }
        return materialChunkRepository.findWithMaterialByMaterialIdIn(materialIds).stream()
                .map(chunk -> new ChunkCandidate(
                        chunk.getId(),
                        chunk.getMaterial().getId(),
                        chunk.getMaterial().getFileName(),
                        chunk.getPageFrom(),
                        chunk.getPageTo(),
                        chunk.getContent(),
                        chunk.getChunkIndex()
                ))
                .toList();
    }

    @Transactional
    public void completeSummaryJob(
            String jobId,
            Long materialId,
            SummaryLength length,
            String content,
            String model,
            String promptVersion) {
        AiJob job = aiJobRepository.findWithUserById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.getStatus() == AiJobStatus.CANCELLED) {
            return;
        }
        Material material = materialRepository.findByIdAndDeletedAtIsNull(materialId)
                .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_NOT_FOUND));

        AiSummary summary = AiSummary.builder()
                .material(material)
                .user(job.getUser())
                .length(length != null ? length : SummaryLength.MEDIUM)
                .content(content)
                .model(model)
                .promptVersion(promptVersion)
                .sourceJob(job)
                .build();
        summary = aiSummaryRepository.save(summary);

        job.setStatus(AiJobStatus.SUCCEEDED);
        job.setResultType("SUMMARY");
        job.setResultId(summary.getId());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setFinishedAt(Instant.now());
        aiJobRepository.save(job);
    }

    @Transactional
    public void completeExplainJob(
            String jobId,
            Long materialId,
            Map<String, Object> explanationOutput) {
        AiJob job = aiJobRepository.findById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.getStatus() == AiJobStatus.CANCELLED) {
            return;
        }

        Map<String, Object> updatedInput = job.getInputJson() != null
                ? new HashMap<>(job.getInputJson())
                : new HashMap<>();
        updatedInput.put("output", explanationOutput);
        job.setInputJson(updatedInput);
        job.setStatus(AiJobStatus.SUCCEEDED);
        job.setResultType("EXPLANATION");
        job.setResultId(materialId);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setFinishedAt(Instant.now());
        aiJobRepository.save(job);
    }

    @Transactional
    public void completeQuizJob(
            String jobId,
            Long courseId,
            List<Long> materialIds,
            Long topicId,
            String title,
            QuizDifficulty difficulty,
            QuestionType questionType,
            List<AiQuizValidator.GeneratedQuestion> generatedQuestions,
            String model,
            String promptVersion) {
        AiJob job = aiJobRepository.findWithUserById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.getStatus() == AiJobStatus.CANCELLED) {
            return;
        }

        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        Topic topic = topicId != null ? topicRepository.findById(topicId).orElse(null) : null;

        List<Material> materials = materialRepository.findByIdInAndDeletedAtIsNull(materialIds);
        if (materials.isEmpty()) {
            throw new AppException(ErrorCode.MATERIAL_NOT_FOUND);
        }

        Quiz quiz = Quiz.builder()
                .owner(job.getUser())
                .course(course)
                .title(title)
                .difficulty(difficulty != null ? difficulty : QuizDifficulty.MEDIUM)
                .questionCount(generatedQuestions.size())
                .sourceJobId(job.getId())
                .model(model)
                .promptVersion(promptVersion)
                .build();
        quiz = quizRepository.save(quiz);

        List<QuizMaterial> quizMaterials = new ArrayList<>();
        for (Material material : materials) {
            quizMaterials.add(QuizMaterial.builder()
                    .id(new QuizMaterialId(quiz.getId(), material.getId()))
                    .quiz(quiz)
                    .material(material)
                    .build());
        }
        quizMaterialRepository.saveAll(quizMaterials);

        List<Long> chunkIds = generatedQuestions.stream()
                .map(AiQuizValidator.GeneratedQuestion::sourceChunkId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, MaterialChunk> chunkMap = chunkIds.isEmpty()
                ? Map.of()
                : materialChunkRepository.findAllById(chunkIds).stream()
                        .collect(Collectors.toMap(MaterialChunk::getId, Function.identity()));

        List<QuestionOption> allOptions = new ArrayList<>();
        for (int index = 0; index < generatedQuestions.size(); index++) {
            AiQuizValidator.GeneratedQuestion generated = generatedQuestions.get(index);
            MaterialChunk sourceChunk = generated.sourceChunkId() != null
                    ? chunkMap.get(generated.sourceChunkId())
                    : null;

            Question question = Question.builder()
                    .quiz(quiz)
                    .topic(topic)
                    .position(index + 1)
                    .content(generated.content())
                    .type(questionType != null ? questionType : QuestionType.SINGLE_CHOICE)
                    .explanation(generated.explanation())
                    .sourceChunk(sourceChunk)
                    .build();
            question = questionRepository.save(question);

            for (AiQuizValidator.GeneratedOption opt : generated.options()) {
                allOptions.add(QuestionOption.builder()
                        .question(question)
                        .label(opt.label())
                        .content(opt.content())
                        .isCorrect(opt.isCorrect())
                        .build());
            }
        }
        questionOptionRepository.saveAll(allOptions);

        job.setStatus(AiJobStatus.SUCCEEDED);
        job.setResultType("QUIZ");
        job.setResultId(quiz.getId());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setFinishedAt(Instant.now());
        aiJobRepository.save(job);
    }

    @Transactional
    public void completeFlashcardJob(
            String jobId,
            Long deckId,
            Long courseId,
            String deckTitle,
            List<AiFlashcardValidator.GeneratedFlashcard> generatedCards) {
        AiJob job = aiJobRepository.findWithUserById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.getStatus() == AiJobStatus.CANCELLED) {
            return;
        }

        FlashcardDeck deck;
        if (deckId != null) {
            deck = flashcardDeckRepository.findWithDetailsById(deckId)
                    .orElseThrow(() -> new AppException(ErrorCode.FLASHCARD_DECK_NOT_FOUND));
            if (!deck.getOwner().getId().equals(job.getUser().getId())) {
                throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
            }
        } else {
            Course course = courseId != null
                    ? courseRepository.findByIdAndDeletedAtIsNull(courseId).orElse(null)
                    : null;
            deck = flashcardDeckRepository.save(FlashcardDeck.builder()
                    .owner(job.getUser())
                    .course(course)
                    .title(deckTitle)
                    .build());
        }

        List<Long> chunkIds = generatedCards.stream()
                .map(AiFlashcardValidator.GeneratedFlashcard::sourceChunkId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, MaterialChunk> chunkMap = chunkIds.isEmpty()
                ? Map.of()
                : materialChunkRepository.findAllById(chunkIds).stream()
                        .collect(Collectors.toMap(MaterialChunk::getId, Function.identity()));

        List<Flashcard> cards = new ArrayList<>(generatedCards.size());
        for (AiFlashcardValidator.GeneratedFlashcard generated : generatedCards) {
            MaterialChunk sourceChunk = generated.sourceChunkId() != null
                    ? chunkMap.get(generated.sourceChunkId())
                    : null;
            cards.add(Flashcard.builder()
                    .deck(deck)
                    .front(generated.front())
                    .back(generated.back())
                    .sourceChunk(sourceChunk)
                    .build());
        }
        flashcardRepository.saveAll(cards);

        job.setStatus(AiJobStatus.SUCCEEDED);
        job.setResultType("FLASHCARD_DECK");
        job.setResultId(deck.getId());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setFinishedAt(Instant.now());
        aiJobRepository.save(job);
    }

    public record BusyEventSummary(
            String title,
            String type,
            Instant startAt,
            Instant endAt
    ) {
    }

    public record WeakTopicSummary(
            String topicTitle,
            double accuracyRate,
            long sampleSize
    ) {
    }

    public record StudyPlanContext(
            String goalTitle,
            String subject,
            LocalDate goalDueDate,
            GoalPriority priority,
            Map<String, Object> freeTime,
            List<BusyEventSummary> busyEvents,
            List<DueAssignmentSummary> dueAssignments,
            List<WeakTopicSummary> weakTopics
    ) {
    }

    @Transactional(readOnly = true)
    public StudyPlanContext loadStudyPlanContext(Long userId, Long goalId, LocalDate fromDate, LocalDate toDate) {
        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.GOAL_NOT_FOUND));

        Instant rangeFrom = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant rangeTo = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Long> activeClassIds = classMemberRepository.findActiveClassRolesByUserId(userId).stream()
                .map(ActiveClassRoleProjection::getClassId)
                .distinct()
                .toList();
        List<Event> events = activeClassIds.isEmpty()
                ? eventRepository.findOwnerEventsInRange(userId, rangeFrom, rangeTo)
                : eventRepository.findCalendarEventsInRange(userId, activeClassIds, rangeFrom, rangeTo);
        List<BusyEventSummary> busyEvents = events.stream()
                .map(e -> new BusyEventSummary(
                        e.getTitle(),
                        e.getType().name(),
                        e.getStartAt(),
                        e.getEndAt()
                ))
                .toList();

        List<DueAssignmentSummary> dueAssignments = assignmentRepository.findUpcomingUnsubmittedForStudent(
                userId,
                Instant.now(),
                PageRequest.of(0, 10)
        );

        List<WeakTopicSummary> weakTopics = attemptAnswerRepository.findTopicPerformance(userId, AttemptStatus.SUBMITTED)
                .stream()
                .map(row -> {
                    long total = row.getSampleSize() != null ? row.getSampleSize() : 0L;
                    long correct = row.getCorrectCount() != null ? row.getCorrectCount() : 0L;
                    double accuracy = total > 0 ? Math.round((correct * 1000.0) / total) / 10.0 : 0.0;
                    return new WeakTopicSummary(row.getTopicTitle(), accuracy, total);
                })
                .filter(topic -> topic.accuracyRate() < 85.0 || topic.sampleSize() >= 3)
                .limit(10)
                .toList();

        return new StudyPlanContext(
                goal.getTitle(),
                goal.getSubject(),
                goal.getDueDate(),
                goal.getPriority(),
                goal.getFreeTime() != null ? goal.getFreeTime() : Map.of(),
                busyEvents,
                dueAssignments,
                weakTopics
        );
    }

    @Transactional
    public void completeStudyPlanJob(
            String jobId,
            AiStudyPlanValidator.GeneratedStudyPlan generatedPlan,
            String model,
            String promptVersion) {
        AiJob job = aiJobRepository.findWithUserById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.getStatus() == AiJobStatus.CANCELLED) {
            return;
        }

        StudyPlan plan = StudyPlan.builder()
                .user(job.getUser())
                .job(job)
                .status(StudyPlanStatus.PROPOSED)
                .rationale(generatedPlan.rationale())
                .model(model)
                .promptVersion(promptVersion)
                .items(new ArrayList<>())
                .build();

        for (AiStudyPlanValidator.GeneratedPlanItem itemDto : generatedPlan.items()) {
            StudyPlanItem item = StudyPlanItem.builder()
                    .plan(plan)
                    .title(itemDto.title())
                    .plannedStart(itemDto.plannedStart())
                    .plannedEnd(itemDto.plannedEnd())
                    .decision(StudyPlanItemDecision.PENDING)
                    .build();
            plan.getItems().add(item);
        }

        StudyPlan savedPlan = studyPlanRepository.save(plan);

        job.setStatus(AiJobStatus.SUCCEEDED);
        job.setResultType("STUDY_PLAN");
        job.setResultId(savedPlan.getId());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setFinishedAt(Instant.now());
        aiJobRepository.save(job);
    }

    @Transactional
    public void failJob(String jobId, String errorCode, String errorMessage) {
        aiJobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == AiJobStatus.CANCELLED) {
                return;
            }
            job.setStatus(AiJobStatus.FAILED);
            job.setErrorCode(errorCode);
            job.setErrorMessage(errorMessage);
            job.setFinishedAt(Instant.now());
            aiJobRepository.save(job);
        });
    }

    @Transactional(readOnly = true)
    public Optional<AiJobResponse> findJobResponse(String jobId) {
        return aiJobRepository.findById(jobId).map(aiMapper::toJobResponse);
    }
}
