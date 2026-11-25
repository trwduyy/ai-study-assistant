package com.aistudy.assistant.classroom.event;

/**
 * Domain event emitted when a student or co-teacher becomes an active member of a classroom.
 */
public record ClassMemberActivatedEvent(
        Long classId,
        Long userId
) {
}
