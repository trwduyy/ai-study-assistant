package com.aistudy.assistant.ai.repository;

import com.aistudy.assistant.ai.entity.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {
}
