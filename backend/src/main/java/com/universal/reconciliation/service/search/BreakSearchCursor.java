package com.universal.reconciliation.service.search;

import java.time.Instant;
import java.util.Objects;

/**
 * Cursor token for keyset pagination. Encodes the last seen run timestamp and
 * break identifier so subsequent lookups can resume efficiently.
 */
public record BreakSearchCursor(Instant runDateTime, long breakId) {

    private static final String DELIMITER = "::";

    public String toToken() {
        return runDateTime.toEpochMilli() + DELIMITER + breakId;
    }

    public static BreakSearchCursor fromToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split(DELIMITER);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid cursor token: " + token);
        }
        long epochMillis = Long.parseLong(parts[0]);
        long id = Long.parseLong(parts[1]);
        return new BreakSearchCursor(Instant.ofEpochMilli(epochMillis), id);
    }

    public boolean isBefore(Instant otherRunDateTime, long otherBreakId) {
        Objects.requireNonNull(otherRunDateTime, "otherRunDateTime");
        if (otherRunDateTime.isBefore(runDateTime)) {
            return true;
        }
        if (otherRunDateTime.equals(runDateTime)) {
            return otherBreakId < breakId;
        }
        return false;
    }
}

