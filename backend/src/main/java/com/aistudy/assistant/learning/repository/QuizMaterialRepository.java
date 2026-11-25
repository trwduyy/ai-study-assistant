package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.entity.QuizMaterial;
import com.aistudy.assistant.learning.entity.QuizMaterialId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizMaterialRepository extends JpaRepository<QuizMaterial, QuizMaterialId> {
}
