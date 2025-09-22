package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.SystemActivityDto;
import com.universal.reconciliation.domain.entity.SystemActivityLog;
import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.repository.SystemActivityLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures and exposes system activity entries for the Phase 2 audit feed.
 */
@Service
public class SystemActivityService {

    private final SystemActivityLogRepository repository;

    public SystemActivityService(SystemActivityLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordEvent(SystemEventType eventType, String details) {
        SystemActivityLog log = new SystemActivityLog();
        log.setEventType(eventType);
        log.setDetails(details);
        log.setRecordedAt(Instant.now());
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<SystemActivityDto> fetchRecent() {
        return repository.findTop20ByOrderByRecordedAtDesc().stream()
                .map(log -> new SystemActivityDto(log.getId(), log.getEventType(), log.getDetails(), log.getRecordedAt()))
                .toList();
    }
}

