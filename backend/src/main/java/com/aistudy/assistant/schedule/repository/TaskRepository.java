package com.aistudy.assistant.schedule.repository;

import com.aistudy.assistant.schedule.entity.Task;
import com.aistudy.assistant.schedule.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"user", "goal"})
    Optional<Task> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"goal"})
    @Query("""
            SELECT t
            FROM Task t
            WHERE t.user.id = :userId
              AND (:from IS NULL OR t.taskDate >= :from)
              AND (:to IS NULL OR t.taskDate <= :to)
              AND (:status IS NULL OR t.status = :status)
              AND (:goalId IS NULL OR t.goal.id = :goalId)
            """)
    Page<Task> findByUserIdAndFilters(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") TaskStatus status,
            @Param("goalId") Long goalId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"goal"})
    @Query("""
            SELECT t
            FROM Task t
            WHERE t.user.id = :userId
              AND t.taskDate = :today
            ORDER BY t.priority DESC, t.remindAt ASC, t.id ASC
            """)
    List<Task> findTodayTasksForDashboard(
            @Param("userId") Long userId,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT t
            FROM Task t
            WHERE t.status = com.aistudy.assistant.schedule.entity.TaskStatus.TODO
              AND t.remindAt IS NOT NULL
              AND t.remindAt >= :windowStart
              AND t.remindAt <= :windowEnd
            ORDER BY t.remindAt ASC, t.id ASC
            """)
    List<Task> findTasksDueForReminder(
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Task t
            SET t.status = com.aistudy.assistant.schedule.entity.TaskStatus.MISSED,
                t.updatedAt = :now
            WHERE t.status = com.aistudy.assistant.schedule.entity.TaskStatus.TODO
              AND t.taskDate < :today
            """)
    int markOverdueTasksAsMissed(
            @Param("today") LocalDate today,
            @Param("now") Instant now
    );
}
