package com.universal.reconciliation.domain.enums;

/**
 * Enumerates the supported ingestion adapter families that can load
 * reconciliation source data. Additional adapter types can be introduced
 * without impacting the reconciliation engine itself.
 */
public enum IngestionAdapterType {
    CSV_FILE,
    FIXED_WIDTH_FILE,
    XML_FILE,
    JSON_FILE,
    DATABASE,
    REST_API,
    MESSAGE_QUEUE,
    LLM_DOCUMENT
}
