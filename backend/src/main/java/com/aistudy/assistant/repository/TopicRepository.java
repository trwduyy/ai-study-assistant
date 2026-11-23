package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByCourseIdOrderByOrderIndexAscIdAsc(Long courseId);

    Optional<Topic> findByIdAndCourseId(Long id, Long courseId);

    Optional<Topic> findByIdAndCourseUserId(Long id, Long userId);

    boolean existsByIdAndCourseId(Long id, Long courseId);
}
