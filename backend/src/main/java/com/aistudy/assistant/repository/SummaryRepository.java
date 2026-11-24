package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.Summary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SummaryRepository extends JpaRepository<Summary, Long> {

    List<Summary> findByMaterialIdAndUserIdOrderByCreatedAtDesc(Long materialId, Long userId);

    Optional<Summary> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
