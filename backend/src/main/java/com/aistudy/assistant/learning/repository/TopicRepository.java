package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    @Query("""
            SELECT t
            FROM Topic t
            WHERE t.chapter.id = :chapterId
              AND t.chapter.course.deletedAt IS NULL
            ORDER BY t.position ASC, t.id ASC
            """)
    List<Topic> findByChapterIdOrdered(@Param("chapterId") Long chapterId);

    @Query("""
            SELECT t
            FROM Topic t
            WHERE t.chapter.id IN :chapterIds
            ORDER BY t.chapter.id ASC, t.position ASC, t.id ASC
            """)
    List<Topic> findByChapterIdsOrdered(@Param("chapterIds") List<Long> chapterIds);

    @Query("""
            SELECT t
            FROM Topic t
            JOIN FETCH t.chapter ch
            JOIN FETCH ch.course c
            JOIN FETCH c.owner
            WHERE t.id = :topicId
              AND c.deletedAt IS NULL
            """)
    Optional<Topic> findWithChapterCourseAndOwnerById(@Param("topicId") Long topicId);

    @Query("""
            SELECT COUNT(t) > 0
            FROM Topic t
            WHERE t.id = :topicId
              AND t.chapter.course.id = :courseId
              AND t.chapter.course.deletedAt IS NULL
            """)
    boolean existsByIdAndCourseId(@Param("topicId") Long topicId, @Param("courseId") Long courseId);
}
