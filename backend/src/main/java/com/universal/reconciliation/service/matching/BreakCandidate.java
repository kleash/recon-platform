package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.enums.BreakType;
import java.util.Map;

/**
 * Represents a potential break produced by the matching engine prior to persistence.
 */
public record BreakCandidate(BreakType type, Map<String, Object> sourceA, Map<String, Object> sourceB) {
}
