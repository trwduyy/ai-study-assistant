package com.aistudy.assistant.schedule.repository;

import com.aistudy.assistant.schedule.entity.StudyPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    @EntityGraph(attributePaths = {"user", "job", "items", "items.task"})
    Optional<StudyPlan> findWithItemsById(Long id);
}
