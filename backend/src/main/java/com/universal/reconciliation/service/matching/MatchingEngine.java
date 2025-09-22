package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;

/**
 * Simple interface for matching implementations.
 */
public interface MatchingEngine {

    MatchingResult execute(ReconciliationDefinition definition);
}
