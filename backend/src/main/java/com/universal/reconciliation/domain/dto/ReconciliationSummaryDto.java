package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.TriggerType;
import java.time.Instant;

/**
 * Provides headline metrics for a reconciliation run.
 */
public record ReconciliationSummaryDto(
        Long definitionId,
        Long runId,
        Instant runDateTime,
        TriggerType triggerType,
        String triggeredBy,
        String triggerCorrelationId,
        String triggerComments,
        int matched,
        int mismatched,
        int missing) {}
