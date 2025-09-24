package com.universal.reconciliation.domain.enums;

/**
 * Represents the lifecycle of a staged data batch captured for a
 * reconciliation source.
 */
public enum DataBatchStatus {
    PENDING,
    LOADING,
    COMPLETE,
    FAILED,
    ARCHIVED
}
