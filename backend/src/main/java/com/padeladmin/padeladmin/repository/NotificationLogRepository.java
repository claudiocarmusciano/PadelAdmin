package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.NotificationLog;
import com.padeladmin.padeladmin.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByPlayerIdOrderBySentAtDesc(Long playerId);
    List<NotificationLog> findByStatus(NotificationStatus status);
}
