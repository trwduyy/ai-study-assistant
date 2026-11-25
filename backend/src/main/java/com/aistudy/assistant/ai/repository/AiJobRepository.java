package com.aistudy.assistant.ai.repository;

import com.aistudy.assistant.ai.entity.AiJob;
import com.aistudy.assistant.ai.entity.AiJobStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiJobRepository extends JpaRepository<AiJob, String> {

    @EntityGraph(attributePaths = "user")
    Optional<AiJob> findWithUserById(String id);

    Optional<AiJob> findByIdAndUserId(String id, Long userId);

    List<AiJob> findTop20ByStatusOrderByCreatedAtAsc(AiJobStatus status);

    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId,
            Instant startInclusive,
            Instant endExclusive
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AiJob j
            SET j.status = :runningStatus,
                j.attempts = j.attempts + 1,
                j.updatedAt = :now
            WHERE j.id = :jobId
              AND j.status = :pendingStatus
            """)
    int claimPendingJob(
            @Param("jobId") String jobId,
            @Param("pendingStatus") AiJobStatus pendingStatus,
            @Param("runningStatus") AiJobStatus runningStatus,
            @Param("now") Instant now
    );
}
