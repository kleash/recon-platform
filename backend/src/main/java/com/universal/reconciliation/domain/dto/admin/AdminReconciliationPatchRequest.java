package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;

/**
 * Partial update payload for reconciliation metadata allowing lightweight adjustments
 * without re-posting the full aggregate.
 */
public record AdminReconciliationPatchRequest(
        ReconciliationLifecycleStatus status,
        Boolean makerCheckerEnabled,
        String notes,
        String owner,
        Boolean autoTriggerEnabled,
        String autoTriggerCron,
        String autoTriggerTimezone,
        Integer autoTriggerGraceMinutes) {}

