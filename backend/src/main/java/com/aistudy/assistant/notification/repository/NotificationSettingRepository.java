package com.aistudy.assistant.notification.repository;

import com.aistudy.assistant.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByUserIdOrderByTypeAsc(Long userId);

    Optional<NotificationSetting> findByUserIdAndType(Long userId, String type);
}
