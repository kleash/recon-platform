package com.universal.reconciliation.service.ingestion;

import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import java.util.List;
import java.util.Map;

/**
 * Contract for pluggable ingestion adapters. Each adapter is responsible for
 * reading raw records from the underlying channel and returning them as a
 * list of column/value maps.
 */
public interface IngestionAdapter {

    IngestionAdapterType getType();

    List<Map<String, Object>> readRecords(IngestionAdapterRequest request);
}
