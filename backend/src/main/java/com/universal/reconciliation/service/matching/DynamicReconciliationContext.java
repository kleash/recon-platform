package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import java.util.List;

/**
 * Aggregates the metadata and staged data needed for the dynamic matching
 * engine to execute.
 */
public record DynamicReconciliationContext(
        ReconciliationDefinition definition,
        List<CanonicalField> canonicalFields,
        List<CanonicalField> keyFields,
        List<CanonicalField> compareFields,
        List<CanonicalField> classifierFields,
        DynamicSourceDataset anchor,
        List<DynamicSourceDataset> otherSources) {}
