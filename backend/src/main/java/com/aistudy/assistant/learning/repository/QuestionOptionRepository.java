package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    @Query("""
            SELECT o
            FROM QuestionOption o
            WHERE o.question.id IN :questionIds
            ORDER BY o.question.id ASC, o.label ASC, o.id ASC
            """)
    List<QuestionOption> findByQuestionIdInOrderByLabel(@Param("questionIds") Collection<Long> questionIds);
}
