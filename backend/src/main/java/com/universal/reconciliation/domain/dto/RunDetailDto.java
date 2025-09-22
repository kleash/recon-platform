package com.universal.reconciliation.domain.dto;

import java.util.List;

/**
 * Contains summary metrics and break details for a reconciliation run.
 */
public record RunDetailDto(
        ReconciliationSummaryDto summary,
        RunAnalyticsDto analytics,
        List<BreakItemDto> breaks,
        FilterMetadataDto filters) {}
