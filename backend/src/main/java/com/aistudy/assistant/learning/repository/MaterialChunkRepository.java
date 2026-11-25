package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.dto.MaterialChunkResponse;
import com.aistudy.assistant.learning.entity.MaterialChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MaterialChunkRepository extends JpaRepository<MaterialChunk, Long> {

    List<MaterialChunk> findByMaterialIdOrderByChunkIndexAsc(Long materialId);

    @Query("""
            SELECT mc
            FROM MaterialChunk mc
            JOIN FETCH mc.material m
            WHERE m.id IN :materialIds
              AND m.deletedAt IS NULL
            ORDER BY m.id ASC, mc.chunkIndex ASC
            """)
    List<MaterialChunk> findWithMaterialByMaterialIdIn(@Param("materialIds") Collection<Long> materialIds);

    long countByMaterialId(Long materialId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MaterialChunk mc WHERE mc.material.id = :materialId")
    int deleteByMaterialId(@Param("materialId") Long materialId);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.learning.dto.MaterialChunkResponse(
                        mc.id,
                        mc.chunkIndex,
                        mc.pageFrom,
                        mc.pageTo,
                        mc.content,
                        mc.tokenCount
                    )
                    FROM MaterialChunk mc
                    WHERE mc.material.id = :materialId
                      AND (:pageFrom IS NULL OR mc.pageTo IS NULL OR mc.pageTo >= :pageFrom)
                      AND (:pageTo IS NULL OR mc.pageFrom IS NULL OR mc.pageFrom <= :pageTo)
                    ORDER BY mc.chunkIndex ASC
                    """,
            countQuery = """
                    SELECT COUNT(mc)
                    FROM MaterialChunk mc
                    WHERE mc.material.id = :materialId
                      AND (:pageFrom IS NULL OR mc.pageTo IS NULL OR mc.pageTo >= :pageFrom)
                      AND (:pageTo IS NULL OR mc.pageFrom IS NULL OR mc.pageFrom <= :pageTo)
                    """
    )
    Page<MaterialChunkResponse> findProjectedByMaterialId(
            @Param("materialId") Long materialId,
            @Param("pageFrom") Integer pageFrom,
            @Param("pageTo") Integer pageTo,
            Pageable pageable
    );
}
