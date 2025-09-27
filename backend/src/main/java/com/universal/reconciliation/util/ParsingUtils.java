package com.universal.reconciliation.util;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared helpers for parsing primitive values from HTTP request parameters and payloads.
 */
public final class ParsingUtils {

    private ParsingUtils() {}

    public static int parseIntOrDefault(String value, int defaultValue, String parameterName) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid integer value for parameter '" + parameterName + "'",
                    ex);
        }
    }

    public static boolean parseFlexibleBoolean(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Boolean value cannot be null");
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String string = value.toString().trim().toLowerCase(Locale.ROOT);
        return switch (string) {
            case "true", "1", "yes", "y" -> Boolean.TRUE;
            case "false", "0", "no", "n" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("Unable to parse boolean value: " + value);
        };
    }
}
