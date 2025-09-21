package com.universal.reconciliation.domain.dto;

import java.time.Instant;

/**
 * Provides headline metrics for a reconciliation run.
 */
public record ReconciliationSummaryDto(Long runId, Instant runDateTime, int matched, int mismatched, int missing) {
}
