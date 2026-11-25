package com.aistudy.assistant.ai.repository;

import com.aistudy.assistant.ai.entity.AiMessageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AiMessageSourceRepository extends JpaRepository<AiMessageSource, Long> {

    @Query("""
            SELECT ms
            FROM AiMessageSource ms
            JOIN FETCH ms.material m
            LEFT JOIN FETCH ms.chunk c
            WHERE ms.message.id IN :messageIds
            ORDER BY ms.id ASC
            """)
    List<AiMessageSource> findWithMaterialByMessageIdIn(@Param("messageIds") Collection<Long> messageIds);
}
