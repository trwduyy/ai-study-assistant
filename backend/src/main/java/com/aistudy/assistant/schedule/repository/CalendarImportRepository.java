package com.aistudy.assistant.schedule.repository;

import com.aistudy.assistant.schedule.entity.CalendarImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CalendarImportRepository extends JpaRepository<CalendarImport, Long> {

    Optional<CalendarImport> findByIdAndUserId(Long id, Long userId);
}
