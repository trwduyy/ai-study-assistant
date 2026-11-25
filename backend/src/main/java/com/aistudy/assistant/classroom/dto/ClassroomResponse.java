package com.aistudy.assistant.classroom.dto;

import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Projected classroom response DTO containing summary counts and caller's active role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String subject;
    private String avatarUrl;
    private Instant startAt;
    private Instant endAt;
    private ClassroomStatus status;
    private Long ownerId;
    private String ownerName;
    private ClassRole myClassRole;
    private Long memberCount;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
