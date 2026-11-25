package com.aistudy.assistant.assignment.mapper;

import com.aistudy.assistant.assignment.dto.AssignmentAttachmentDto;
import com.aistudy.assistant.assignment.dto.AssignmentResponse;
import com.aistudy.assistant.assignment.dto.ClassTestResponse;
import com.aistudy.assistant.assignment.dto.SubmissionFileDto;
import com.aistudy.assistant.assignment.dto.SubmissionResponse;
import com.aistudy.assistant.assignment.entity.Assignment;
import com.aistudy.assistant.assignment.entity.AssignmentAttachment;
import com.aistudy.assistant.assignment.entity.ClassTest;
import com.aistudy.assistant.assignment.entity.Submission;
import com.aistudy.assistant.assignment.entity.SubmissionFile;
import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps {@code assignment} module entities to REST response DTOs.
 */
@Component
public class AssignmentMapper {

    public AssignmentResponse toAssignmentResponse(
            Assignment assignment,
            List<AssignmentAttachment> attachments,
            Long mySubmissionId,
            SubmissionStatus mySubmissionStatus) {
        List<AssignmentAttachmentDto> attachmentDtos = attachments == null
                ? List.of()
                : attachments.stream()
                        .map(att -> new AssignmentAttachmentDto(att.getId(), att.getFileName()))
                        .toList();

        return AssignmentResponse.builder()
                .id(assignment.getId())
                .classId(assignment.getClassroom().getId())
                .creatorId(assignment.getCreator().getId())
                .title(assignment.getTitle())
                .instructions(assignment.getInstructions())
                .maxScore(assignment.getMaxScore())
                .openAt(assignment.getOpenAt())
                .dueAt(assignment.getDueAt())
                .allowLate(assignment.getAllowLate())
                .maxSubmissions(assignment.getMaxSubmissions())
                .attachments(attachmentDtos)
                .mySubmissionId(mySubmissionId)
                .mySubmissionStatus(mySubmissionStatus)
                .version(assignment.getVersion())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }

    public SubmissionResponse toSubmissionResponse(Submission submission, List<SubmissionFile> files) {
        List<SubmissionFileDto> fileDtos = files == null
                ? List.of()
                : files.stream()
                        .map(f -> new SubmissionFileDto(f.getId(), f.getFileName(), f.getSizeBytes()))
                        .toList();

        return SubmissionResponse.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignment().getId())
                .studentId(submission.getStudent().getId())
                .studentName(submission.getStudent().getFullName())
                .textAnswer(submission.getTextAnswer())
                .status(submission.getStatus())
                .late(submission.getIsLate())
                .reopened(submission.isReopenedForResubmission())
                .submittedAt(submission.getSubmittedAt())
                .score(submission.getScore())
                .feedback(submission.getFeedback())
                .gradedBy(submission.getGradedBy() != null ? submission.getGradedBy().getId() : null)
                .gradedAt(submission.getGradedAt())
                .files(fileDtos)
                .version(submission.getVersion())
                .build();
    }

    public ClassTestResponse toClassTestResponse(ClassTest test, Long myAttemptsUsed) {
        return ClassTestResponse.builder()
                .id(test.getId())
                .classId(test.getClassroom().getId())
                .quizId(test.getQuiz().getId())
                .title(test.getTitle())
                .startAt(test.getStartAt())
                .endAt(test.getEndAt())
                .durationMinutes(test.getDurationMinutes())
                .maxAttempts(test.getMaxAttempts())
                .shuffleQuestions(test.getShuffleQuestions())
                .myAttemptsUsed(myAttemptsUsed != null ? myAttemptsUsed : 0L)
                .createdAt(test.getCreatedAt())
                .updatedAt(test.getUpdatedAt())
                .build();
    }
}
