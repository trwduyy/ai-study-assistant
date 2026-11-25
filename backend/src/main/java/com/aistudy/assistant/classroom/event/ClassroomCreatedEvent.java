package com.aistudy.assistant.classroom.event;

/**
 * Domain event emitted within the classroom creation transaction to provision the class chat room.
 */
public record ClassroomCreatedEvent(
        Long classId,
        Long ownerId
) {
}
