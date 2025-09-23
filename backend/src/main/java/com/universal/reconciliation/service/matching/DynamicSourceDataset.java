package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import java.util.Map;

/**
 * Represents the staged records for a configured reconciliation source.
 */
public record DynamicSourceDataset(
        ReconciliationSource source,
        SourceDataBatch batch,
        Map<String, Map<String, Object>> recordsByKey) {
}
