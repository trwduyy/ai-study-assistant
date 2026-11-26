package com.aistudy.assistant.schedule.repository;

import com.aistudy.assistant.schedule.entity.Goal;
import com.aistudy.assistant.schedule.entity.GoalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT g
            FROM Goal g
            WHERE g.user.id = :userId
              AND (:status IS NULL OR g.status = :status)
            """)
    Page<Goal> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") GoalStatus status,
            Pageable pageable
    );

    List<Goal> findTop5ByUserIdAndStatusOrderByDueDateAscIdAsc(Long userId, GoalStatus status);
}
