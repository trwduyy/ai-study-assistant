package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Material> findByCourseIdAndUserIdOrderByCreatedAtDesc(Long courseId, Long userId);

    List<Material> findByCourseIdAndTopicIdAndUserIdOrderByCreatedAtDesc(Long courseId, Long topicId, Long userId);

    Optional<Material> findByIdAndUserId(Long id, Long userId);

    long countByCourseId(Long courseId);

    long countByTopicId(Long topicId);
}
