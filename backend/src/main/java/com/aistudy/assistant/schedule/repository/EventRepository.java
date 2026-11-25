package com.aistudy.assistant.schedule.repository;

import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = {"owner", "classroom"})
    Optional<Event> findWithDetailsById(Long id);

    Optional<Event> findByOwnerIdAndSourceAndExternalId(Long ownerId, EventSource source, String externalId);

    List<Event> findByOwnerIdAndSourceAndExternalIdIn(
            Long ownerId,
            EventSource source,
            Collection<String> externalIds
    );

    void deleteByOwnerIdAndSource(Long ownerId, EventSource source);

    @EntityGraph(attributePaths = {"owner", "classroom"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.classroom IS NULL
              AND e.owner.id = :ownerId
              AND e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND e.startAt < :to
              AND e.endAt > :from
            ORDER BY e.startAt ASC, e.id ASC
            """)
    List<Event> findOwnerEventsInRange(
            @Param("ownerId") Long ownerId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @EntityGraph(attributePaths = {"owner", "classroom"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND e.startAt < :to
              AND e.endAt > :from
              AND (
                  (e.classroom IS NULL AND e.owner.id = :ownerId)
                  OR e.classroom.id IN :classIds
              )
            ORDER BY e.startAt ASC, e.id ASC
            """)
    List<Event> findCalendarEventsInRange(
            @Param("ownerId") Long ownerId,
            @Param("classIds") Collection<Long> classIds,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @EntityGraph(attributePaths = {"owner", "classroom"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.classroom IS NULL
              AND e.owner.id = :ownerId
              AND e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND e.startAt < :to
              AND e.endAt > :from
              AND (:excludeEventId IS NULL OR e.id <> :excludeEventId)
            ORDER BY e.startAt ASC, e.id ASC
            """)
    List<Event> findOverlappingOwnerEvents(
            @Param("ownerId") Long ownerId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeEventId") Long excludeEventId
    );

    @EntityGraph(attributePaths = {"owner", "classroom"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND e.startAt < :to
              AND e.endAt > :from
              AND (:excludeEventId IS NULL OR e.id <> :excludeEventId)
              AND (
                  (e.classroom IS NULL AND e.owner.id = :ownerId)
                  OR e.classroom.id IN :classIds
              )
            ORDER BY e.startAt ASC, e.id ASC
            """)
    List<Event> findOverlappingCalendarEvents(
            @Param("ownerId") Long ownerId,
            @Param("classIds") Collection<Long> classIds,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeEventId") Long excludeEventId
    );

    @EntityGraph(attributePaths = {"owner"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.owner.id = :ownerId
              AND e.classroom IS NULL
              AND e.type = com.aistudy.assistant.schedule.entity.EventType.PERSONAL
              AND e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND (:from IS NULL OR e.endAt > :from)
              AND (:to IS NULL OR e.startAt < :to)
            """)
    Page<Event> findPersonalEventsByOwner(
            @Param("ownerId") Long ownerId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"owner", "classroom"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.classroom.id = :classId
              AND e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND (:from IS NULL OR e.endAt > :from)
              AND (:to IS NULL OR e.startAt < :to)
            """)
    Page<Event> findClassEventsByClassId(
            @Param("classId") Long classId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"owner", "classroom"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.classroom IS NULL
              AND e.owner.id = :ownerId
              AND e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND e.startAt >= :from
              AND e.startAt <= :to
            ORDER BY e.startAt ASC, e.id ASC
            """)
    List<Event> findUpcomingOwnerEventsForDashboard(
            @Param("ownerId") Long ownerId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"owner", "classroom"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND e.startAt >= :from
              AND e.startAt <= :to
              AND (
                  (e.classroom IS NULL AND e.owner.id = :ownerId)
                  OR e.classroom.id IN :classIds
              )
            ORDER BY e.startAt ASC, e.id ASC
            """)
    List<Event> findUpcomingCalendarEventsForDashboard(
            @Param("ownerId") Long ownerId,
            @Param("classIds") Collection<Long> classIds,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"owner", "classroom"})
    @Query("""
            SELECT e
            FROM Event e
            WHERE e.type IN :types
              AND e.status <> com.aistudy.assistant.schedule.entity.EventStatus.CANCELLED
              AND e.startAt >= :windowStart
              AND e.startAt <= :windowEnd
            ORDER BY e.startAt ASC, e.id ASC
            """)
    List<Event> findUpcomingEventsForReminder(
            @Param("types") Collection<EventType> types,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );
}
