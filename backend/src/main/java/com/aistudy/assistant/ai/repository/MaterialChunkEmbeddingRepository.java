package com.aistudy.assistant.ai.repository;

import com.aistudy.assistant.ai.entity.MaterialChunkEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MaterialChunkEmbeddingRepository extends JpaRepository<MaterialChunkEmbedding, Long> {

    List<MaterialChunkEmbedding> findByChunkIdIn(Collection<Long> chunkIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MaterialChunkEmbedding mce WHERE mce.material.id = :materialId")
    int deleteByMaterialId(@Param("materialId") Long materialId);
}
