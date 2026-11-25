package com.aistudy.assistant.schedule.repository;

import com.aistudy.assistant.schedule.entity.ExternalAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalAccountRepository extends JpaRepository<ExternalAccount, Long> {

    Optional<ExternalAccount> findByUserIdAndProvider(Long userId, String provider);

    void deleteByUserIdAndProvider(Long userId, String provider);
}
