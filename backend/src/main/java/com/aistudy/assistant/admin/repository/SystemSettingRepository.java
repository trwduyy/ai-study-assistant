package com.aistudy.assistant.admin.repository;

import com.aistudy.assistant.admin.entity.SystemSetting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    @EntityGraph(attributePaths = {"updatedBy"})
    List<SystemSetting> findAllByOrderBySettingKeyAsc();
}
