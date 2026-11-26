package com.aistudy.assistant.admin.repository;

import com.aistudy.assistant.admin.dto.AuditLogResponse;
import com.aistudy.assistant.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.admin.dto.AuditLogResponse(
                        a.id,
                        u.id,
                        u.email,
                        a.action,
                        a.objectType,
                        a.objectId,
                        a.metadataJson,
                        a.createdAt
                    )
                    FROM AuditLog a
                    LEFT JOIN a.user u
                    WHERE (:userId IS NULL OR u.id = :userId)
                      AND (:action IS NULL OR LOWER(a.action) = LOWER(:action))
                    """,
            countQuery = """
                    SELECT COUNT(a)
                    FROM AuditLog a
                    WHERE (:userId IS NULL OR a.user.id = :userId)
                      AND (:action IS NULL OR LOWER(a.action) = LOWER(:action))
                    """
    )
    Page<AuditLogResponse> findProjectedAuditLogs(
            @Param("userId") Long userId,
            @Param("action") String action,
            Pageable pageable
    );
}
