package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.SystemActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for retrieving and persisting system activity events.
 */
public interface SystemActivityLogRepository extends JpaRepository<SystemActivityLog, Long> {

    List<SystemActivityLog> findTop20ByOrderByRecordedAtDesc();
}

