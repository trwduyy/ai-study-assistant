package com.aistudy.assistant.learning.mapper;

import com.aistudy.assistant.learning.dto.AttemptReviewResponse;
import com.aistudy.assistant.learning.dto.QuizDetailResponse;
import com.aistudy.assistant.learning.dto.QuizSummaryResponse;
import com.aistudy.assistant.learning.dto.QuizTakeOptionDto;
import com.aistudy.assistant.learning.dto.QuizTakeQuestionDto;
import com.aistudy.assistant.learning.dto.ReviewOptionDto;
import com.aistudy.assistant.learning.dto.ReviewQuestionDto;
import com.aistudy.assistant.learning.dto.StartAttemptResponse;
import com.aistudy.assistant.learning.entity.AttemptAnswer;
import com.aistudy.assistant.learning.entity.Question;
import com.aistudy.assistant.learning.entity.QuestionOption;
import com.aistudy.assistant.learning.entity.Quiz;
import com.aistudy.assistant.learning.entity.QuizAttempt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Pure DTO mapper for quizzes and attempts separating take-mode payloads from post-submission review payloads.
 */
@Component
public class QuizMapper {

    public QuizSummaryResponse toSummaryResponse(Quiz quiz) {
        return new QuizSummaryResponse(
                quiz.getId(),
                quiz.getCourse().getId(),
                quiz.getTitle(),
                quiz.getDifficulty(),
                quiz.getQuestionCount(),
                quiz.getModel(),
                quiz.getPromptVersion(),
                quiz.getVersion(),
                quiz.getCreatedAt(),
                quiz.getUpdatedAt()
        );
    }

    public QuizDetailResponse toTakeDetailResponse(
            Quiz quiz,
            List<Question> questions,
            Map<Long, List<QuestionOption>> optionsByQuestionId) {
        List<QuizTakeQuestionDto> questionDtos = questions.stream()
                .map(q -> new QuizTakeQuestionDto(
                        q.getId(),
                        q.getTopic() != null ? q.getTopic().getId() : null,
                        q.getPosition(),
                        q.getContent(),
                        q.getType(),
                        optionsByQuestionId.getOrDefault(q.getId(), List.of()).stream()
                                .map(opt -> new QuizTakeOptionDto(opt.getId(), opt.getLabel(), opt.getContent()))
                                .toList()
                ))
                .toList();

        return new QuizDetailResponse(
                quiz.getId(),
                quiz.getCourse().getId(),
                quiz.getTitle(),
                quiz.getDifficulty(),
                quiz.getQuestionCount(),
                quiz.getVersion(),
                questionDtos
        );
    }

    public StartAttemptResponse toStartAttemptResponse(QuizAttempt attempt) {
        return new StartAttemptResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getDeadlineAt(),
                attempt.getMaxScore() != null ? attempt.getMaxScore().doubleValue() : 10.0,
                attempt.getVersion(),
                List.of()
        );
    }

    public AttemptReviewResponse toReviewResponse(
            QuizAttempt attempt,
            List<Question> questions,
            Map<Long, List<QuestionOption>> optionsByQuestionId,
            Map<Long, AttemptAnswer> answersByQuestionId) {
        List<ReviewQuestionDto> reviewQuestions = questions.stream()
                .map(q -> {
                    AttemptAnswer answer = answersByQuestionId.get(q.getId());
                    List<Long> selectedIds = answer != null && answer.getSelectedOptionIds() != null
                            ? answer.getSelectedOptionIds()
                            : List.of();
                    boolean correct = answer != null && Boolean.TRUE.equals(answer.getIsCorrect());

                    List<ReviewOptionDto> options = optionsByQuestionId.getOrDefault(q.getId(), List.of()).stream()
                            .map(opt -> new ReviewOptionDto(
                                    opt.getId(),
                                    opt.getLabel(),
                                    opt.getContent(),
                                    opt.isCorrectOption()
                            ))
                            .toList();

                    return new ReviewQuestionDto(
                            q.getId(),
                            q.getPosition(),
                            q.getContent(),
                            q.getType(),
                            options,
                            selectedIds,
                            correct,
                            q.getExplanation(),
                            q.getSourceChunk() != null ? q.getSourceChunk().getId() : null
                    );
                })
                .toList();

        return new AttemptReviewResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getQuiz().getTitle(),
                attempt.getStatus(),
                attempt.getScore() != null ? attempt.getScore().doubleValue() : 0.0,
                attempt.getMaxScore() != null ? attempt.getMaxScore().doubleValue() : 10.0,
                attempt.getCorrectCount(),
                attempt.getTotalQuestions(),
                attempt.getDurationSeconds(),
                attempt.getSubmittedAt(),
                reviewQuestions
        );
    }
}
