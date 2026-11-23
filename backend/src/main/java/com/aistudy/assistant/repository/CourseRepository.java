package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Course> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("SELECT COUNT(t) FROM Topic t WHERE t.course.id = :courseId")
    long countTopicsByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(m) FROM Material m WHERE m.course.id = :courseId")
    long countMaterialsByCourseId(@Param("courseId") Long courseId);
}
