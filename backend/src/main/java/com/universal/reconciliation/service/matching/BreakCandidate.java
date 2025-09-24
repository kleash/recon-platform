package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.enums.BreakType;
import java.util.List;
import java.util.Map;

/**
 * Represents a potential break produced by the matching engine prior to persistence.
 */
public record BreakCandidate(
        BreakType type,
        Map<String, Map<String, Object>> sources,
        Map<String, String> classifications,
        List<String> missingSources) {
}
