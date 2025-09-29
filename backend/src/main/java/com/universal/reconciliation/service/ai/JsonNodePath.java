package com.universal.reconciliation.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

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
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '.') {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (escaping) {
            current.append('\\');
        }
        segments.add(current.toString());
        return segments;
    }
}
