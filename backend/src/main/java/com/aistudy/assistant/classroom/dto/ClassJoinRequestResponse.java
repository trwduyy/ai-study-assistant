package com.aistudy.assistant.classroom.dto;

import com.aistudy.assistant.classroom.entity.JoinRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Projected join request DTO including student and decider display info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassJoinRequestResponse {

    private Long id;
    private Long classId;
    private String className;
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private JoinRequestStatus status;
    private Long decidedById;
    private String decidedByName;
    private Instant decidedAt;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;
}
