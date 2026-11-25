package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.Course;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {"topics"})
    Optional<Course> findWithTopicsByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Query("SELECT COUNT(t) FROM Topic t WHERE t.course.id = :courseId")
    long countTopicsByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(m) FROM Material m WHERE m.course.id = :courseId")
    long countMaterialsByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT t.course.id AS courseId, COUNT(t) AS total FROM Topic t WHERE t.course.id IN :courseIds GROUP BY t.course.id")
    List<CourseCountProjection> countTopicsByCourseIds(@Param("courseIds") List<Long> courseIds);

    @Query("SELECT m.course.id AS courseId, COUNT(m) AS total FROM Material m WHERE m.course.id IN :courseIds GROUP BY m.course.id")
    List<CourseCountProjection> countMaterialsByCourseIds(@Param("courseIds") List<Long> courseIds);

    interface CourseCountProjection {
        Long getCourseId();
        Long getTotal();
    }
}
