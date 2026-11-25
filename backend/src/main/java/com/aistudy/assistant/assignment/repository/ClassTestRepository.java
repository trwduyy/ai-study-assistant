package com.aistudy.assistant.assignment.repository;

import com.aistudy.assistant.assignment.dto.ClassTestResponse;
import com.aistudy.assistant.assignment.entity.ClassTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassTestRepository extends JpaRepository<ClassTest, Long> {

    interface TeacherRecentTestProjection {
        Long getTestId();

        Long getClassId();

        String getClassName();

        String getTitle();

        Instant getStartAt();

        Instant getEndAt();

        Long getSubmittedStudents();

        Long getTotalStudents();

        Double getAverageScore();
    }

    @EntityGraph(attributePaths = {"classroom", "quiz", "quiz.owner"})
    Optional<ClassTest> findWithDetailsById(Long id);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.assignment.dto.ClassTestResponse(
                        t.id,
                        t.classroom.id,
                        t.quiz.id,
                        t.title,
                        t.startAt,
                        t.endAt,
                        t.durationMinutes,
                        t.maxAttempts,
                        t.shuffleQuestions,
                        (SELECT COUNT(qa) FROM QuizAttempt qa WHERE qa.testId = t.id AND qa.user.id = :userId),
                        t.createdAt,
                        t.updatedAt
                    )
                    FROM ClassTest t
                    WHERE t.classroom.id = :classId
                    """,
            countQuery = """
                    SELECT COUNT(t)
                    FROM ClassTest t
                    WHERE t.classroom.id = :classId
                    """
    )
    Page<ClassTestResponse> findProjectedByClassIdAndUserId(
            @Param("classId") Long classId,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query("""
            SELECT t.id AS testId,
                   c.id AS classId,
                   c.name AS className,
                   t.title AS title,
                   t.startAt AS startAt,
                   t.endAt AS endAt,
                   (SELECT COUNT(DISTINCT qa.user.id) FROM QuizAttempt qa
                    WHERE qa.testId = t.id
                      AND qa.status = com.aistudy.assistant.learning.entity.AttemptStatus.SUBMITTED) AS submittedStudents,
                   (SELECT COUNT(st) FROM ClassMember st
                    WHERE st.classroom.id = c.id
                      AND st.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
                      AND st.classRole = com.aistudy.assistant.classroom.entity.ClassRole.STUDENT) AS totalStudents,
                   (SELECT CAST(COALESCE(AVG(qa2.score), 0) AS double) FROM QuizAttempt qa2
                    WHERE qa2.testId = t.id
                      AND qa2.status = com.aistudy.assistant.learning.entity.AttemptStatus.SUBMITTED) AS averageScore
            FROM ClassTest t
            JOIN t.classroom c
            JOIN ClassMember tm ON tm.classroom.id = c.id
            WHERE tm.user.id = :teacherId
              AND tm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
              AND tm.classRole IN (
                  com.aistudy.assistant.classroom.entity.ClassRole.OWNER_TEACHER,
                  com.aistudy.assistant.classroom.entity.ClassRole.CO_TEACHER
              )
            ORDER BY t.endAt DESC, t.id DESC
            """)
    List<TeacherRecentTestProjection> findRecentForTeacherDashboard(
            @Param("teacherId") Long teacherId,
            Pageable pageable
    );
}
