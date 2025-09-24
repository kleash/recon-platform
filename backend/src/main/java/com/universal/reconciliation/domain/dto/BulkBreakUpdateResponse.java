package com.universal.reconciliation.domain.dto;

import java.util.List;

/**
 * Aggregates the outcome of a bulk break update request, splitting successes and failures.
 */
public record BulkBreakUpdateResponse(
        List<BreakItemDto> successes,
        List<BulkBreakFailureDto> failures) {}
