package com.universal.reconciliation.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper for navigating dot-separated JSON paths that allows escaping literal dots using a
 * backslash (e.g. {@code details\.total}).
 */
public final class JsonNodePath {

    private JsonNodePath() {}

    public static JsonNode navigate(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return root;
        }
        JsonNode current = root;
        for (String segment : splitSegments(path)) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private static List<String> splitSegments(String path) {
        String[] segments = path.split("(?<!\\)\\.");
        return Arrays.stream(segments)
                .map(segment -> segment.replace("\\.", "."))
                .collect(Collectors.toList());
    }
}
