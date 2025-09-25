package com.universal.reconciliation.domain.enums;

/**
 * Categorises audit events captured for the system activity timeline.
 */
public enum SystemEventType {
    RECONCILIATION_RUN,
    BREAK_STATUS_CHANGE,
    BREAK_COMMENT,
    BREAK_BULK_ACTION,
    REPORT_EXPORT,
    RECONCILIATION_CONFIG_CHANGE,
    INGESTION_BATCH_ACCEPTED
}

