package com.universal.reconciliation.examples.custodian;

import java.time.LocalTime;

/**
 * Represents the two intra-day reconciliation windows that this example simulates.
 */
public enum CutoffCycle {
    MORNING(LocalTime.of(11, 0), "Morning 11:00 ET"),
    EVENING(LocalTime.of(18, 0), "Evening 18:00 ET");

    private final LocalTime cutoff;
    private final String description;

    CutoffCycle(LocalTime cutoff, String description) {
        this.cutoff = cutoff;
        this.description = description;
    }

    public LocalTime cutoff() {
        return cutoff;
    }

    public String description() {
        return description;
    }
}
