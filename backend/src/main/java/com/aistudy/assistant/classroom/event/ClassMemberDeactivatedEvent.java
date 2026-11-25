package com.aistudy.assistant.classroom.event;

/**
 * Domain event emitted when a classroom member leaves or is removed, immediately revoking chat access.
 */
public record ClassMemberDeactivatedEvent(
        Long classId,
        Long userId
) {
}
