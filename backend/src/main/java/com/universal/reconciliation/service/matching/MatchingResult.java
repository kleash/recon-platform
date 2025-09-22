package com.universal.reconciliation.service.matching;

import java.util.List;

/**
 * Captures the aggregated outcome of a matching engine execution.
 */
public record MatchingResult(int matchedCount, int mismatchedCount, int missingCount, List<BreakCandidate> breaks) {
}
