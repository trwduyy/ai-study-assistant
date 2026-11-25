package com.aistudy.assistant.classroom.dto;

import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Projected classroom member DTO for N+1-free member listings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassMemberResponse {

    private Long id;
    private Long classId;
    private Long userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private ClassRole classRole;
    private ClassMemberStatus status;
    private Instant joinedAt;
}
