package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.SystemEventType;
import java.time.Instant;

/**
 * API payload representing a single system activity timeline entry.
 */
public record SystemActivityDto(Long id, SystemEventType eventType, String details, Instant recordedAt) {
}

