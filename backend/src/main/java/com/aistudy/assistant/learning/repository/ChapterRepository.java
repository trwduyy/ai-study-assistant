package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    @Query("""
            SELECT ch
            FROM Chapter ch
            WHERE ch.course.id = :courseId
              AND ch.course.deletedAt IS NULL
            ORDER BY ch.position ASC, ch.id ASC
            """)
    List<Chapter> findByCourseIdOrdered(@Param("courseId") Long courseId);

    @Query("""
            SELECT ch
            FROM Chapter ch
            JOIN FETCH ch.course c
            JOIN FETCH c.owner
            WHERE ch.id = :chapterId
              AND c.deletedAt IS NULL
            """)
    Optional<Chapter> findWithCourseAndOwnerById(@Param("chapterId") Long chapterId);

    long countByCourseId(Long courseId);
}
