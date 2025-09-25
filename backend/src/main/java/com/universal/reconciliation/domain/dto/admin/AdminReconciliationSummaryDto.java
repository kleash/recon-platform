package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import java.time.Instant;

/**
 * Lightweight listing projection for the administration catalog.
 */
public record AdminReconciliationSummaryDto(
        Long id,
        String code,
        String name,
        ReconciliationLifecycleStatus status,
        boolean makerCheckerEnabled,
        Instant updatedAt,
        String owner,
        String updatedBy,
        Instant lastIngestionAt) {}

