package com.universal.reconciliation.domain.enums;

/**
 * Captures the type of difference detected by the matching engine.
 */
public enum BreakType {
    MISMATCH,
    @Deprecated(forRemoval = true)
    MISSING_IN_SOURCE_A,
    @Deprecated(forRemoval = true)
    MISSING_IN_SOURCE_B,
    ANCHOR_MISSING,
    SOURCE_MISSING
}
