package com.aistudy.assistant.admin.repository;

import com.aistudy.assistant.admin.dto.ReportResponse;
import com.aistudy.assistant.admin.entity.Report;
import com.aistudy.assistant.admin.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @EntityGraph(attributePaths = {"reporter", "handledBy"})
    Optional<Report> findWithDetailsById(Long id);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.admin.dto.ReportResponse(
                        r.id,
                        rep.id,
                        rep.fullName,
                        rep.email,
                        r.type,
                        r.targetType,
                        r.targetId,
                        r.detail,
                        r.status,
                        r.resolutionNote,
                        hb.id,
                        hb.fullName,
                        r.handledAt,
                        r.createdAt,
                        r.updatedAt
                    )
                    FROM Report r
                    JOIN r.reporter rep
                    LEFT JOIN r.handledBy hb
                    WHERE (:status IS NULL OR r.status = :status)
                      AND (:type IS NULL OR LOWER(r.type) = LOWER(:type))
                    """,
            countQuery = """
                    SELECT COUNT(r)
                    FROM Report r
                    WHERE (:status IS NULL OR r.status = :status)
                      AND (:type IS NULL OR LOWER(r.type) = LOWER(:type))
                    """
    )
    Page<ReportResponse> findProjectedReports(
            @Param("status") ReportStatus status,
            @Param("type") String type,
            Pageable pageable
    );
}
