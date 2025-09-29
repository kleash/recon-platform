package com.universal.reconciliation.ingestion.sdk.batch;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.util.StringUtils;

final class CsvRenderer {

    private CsvRenderer() {
    }

    static byte[] render(List<Map<String, Object>> rows, List<String> explicitColumns) {
        try (StringWriter writer = new StringWriter()) {
            List<String> headers = determineHeaders(rows, explicitColumns);
            CSVPrinter printer = CSVFormat.DEFAULT
                    .withHeader(headers.toArray(String[]::new))
                    .print(writer);
            for (Map<String, Object> row : rows) {
                List<String> values = new ArrayList<>(headers.size());
                for (String header : headers) {
                    Object value = resolveValue(row, header);
                    values.add(value == null ? "" : String.valueOf(value));
                }
                printer.printRecord(values);
            }
            printer.flush();
            return writer.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render CSV payload", e);
        }
    }

    private static List<String> determineHeaders(List<Map<String, Object>> rows, List<String> explicit) {
        if (explicit != null && !explicit.isEmpty()) {
            return explicit;
        }
        Set<String> headers = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            headers.addAll(row.keySet());
        }
        return new ArrayList<>(headers);
    }

    static Map<String, Object> normalizeKeys(Map<String, Object> row) {
        if (row == null) {
            return Map.of();
        }
        return row.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> normalizeKey(entry.getKey()),
                        Map.Entry::getValue,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new));
    }

    private static String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        return key.trim();
    }

    private static Object resolveValue(Map<String, Object> row, String header) {
        if (row.containsKey(header)) {
            return row.get(header);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(header)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
