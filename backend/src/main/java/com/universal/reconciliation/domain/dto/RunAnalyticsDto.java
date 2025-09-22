package com.universal.reconciliation.domain.dto;

import java.util.Map;

/**
 * Aggregated analytics derived from a reconciliation run's break population.
 */
public record RunAnalyticsDto(
        Map<String, Long> breaksByStatus,
        Map<String, Long> breaksByType,
        Map<String, Long> breaksByProduct,
        Map<String, Long> breaksByEntity,
        Map<String, Long> openBreaksByAgeBucket,
        int filteredBreakCount,
        int totalBreakCount,
        int totalMatchedCount) {

    public static RunAnalyticsDto empty() {
        return new RunAnalyticsDto(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0, 0, 0);
    }
}
