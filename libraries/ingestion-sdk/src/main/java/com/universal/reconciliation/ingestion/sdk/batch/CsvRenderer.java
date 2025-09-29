package com.universal.reconciliation.ingestion.sdk.batch;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
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

    static void streamRows(ResultSet resultSet, List<String> explicitColumns, Writer writer)
            throws SQLException, IOException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        List<String> headers = determineHeaders(metaData, explicitColumns);
        CSVPrinter printer = createPrinter(writer, headers);
        int columnCount = metaData.getColumnCount();
        while (resultSet.next()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                String column = metaData.getColumnLabel(i);
                row.put(column, resultSet.getObject(i));
            }
            printRow(printer, normalizeKeys(row), headers);
        }
        printer.flush();
    }

    static CSVPrinter createPrinter(Writer writer, List<String> headers) throws IOException {
        return CSVFormat.DEFAULT
                .withHeader(headers.toArray(String[]::new))
                .print(writer);
    }

    static List<String> determineHeaders(ResultSetMetaData metaData, List<String> explicitColumns)
            throws SQLException {
        if (explicitColumns != null && !explicitColumns.isEmpty()) {
            return new ArrayList<>(explicitColumns);
        }
        int columnCount = metaData.getColumnCount();
        List<String> headers = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            headers.add(metaData.getColumnLabel(i));
        }
        return headers;
    }

    static List<String> determineHeadersFromRow(Map<String, Object> row, List<String> explicitColumns) {
        if (explicitColumns != null && !explicitColumns.isEmpty()) {
            return new ArrayList<>(explicitColumns);
        }
        return new ArrayList<>(row.keySet());
    }

    static void streamRows(Iterable<Map<String, Object>> rows, List<String> explicitColumns, OutputStream output)
            throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            streamRows(rows, explicitColumns, writer);
            writer.flush();
        }
    }

    static void streamRows(Iterable<Map<String, Object>> rows, List<String> explicitColumns, Writer writer)
            throws IOException {
        java.util.Iterator<Map<String, Object>> iterator = rows.iterator();
        List<String> headers;
        CSVPrinter printer;
        if (iterator.hasNext()) {
            Map<String, Object> first = normalizeKeys(iterator.next());
            headers = determineHeadersFromRow(first, explicitColumns);
            printer = createPrinter(writer, headers);
            printRow(printer, first, headers);
        } else {
            headers = explicitColumns != null ? new ArrayList<>(explicitColumns) : List.of();
            printer = createPrinter(writer, headers);
        }
        while (iterator.hasNext()) {
            Map<String, Object> row = normalizeKeys(iterator.next());
            printRow(printer, row, headers);
        }
        printer.flush();
    }

    static void streamIterator(Iterator<Map<String, Object>> iterator, List<String> explicitColumns, Writer writer)
            throws IOException {
        AutoCloseable closeable = iterator instanceof AutoCloseable ? (AutoCloseable) iterator : null;
        List<String> headers = explicitColumns != null && !explicitColumns.isEmpty() ? new ArrayList<>(explicitColumns) : null;
        CSVPrinter printer;
        try {
            if (iterator.hasNext()) {
                Map<String, Object> first = normalizeKeys(iterator.next());
                if (headers == null) {
                    headers = determineHeadersFromRow(first, null);
                }
                printer = createPrinter(writer, headers);
                printRow(printer, first, headers);
            } else {
                headers = headers != null ? headers : List.of();
                printer = createPrinter(writer, headers);
            }
            while (iterator.hasNext()) {
                Map<String, Object> row = normalizeKeys(iterator.next());
                printRow(printer, row, headers);
            }
            printer.flush();
        } catch (RuntimeException | IOException e) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception suppressed) {
                    if (e instanceof IOException ioException) {
                        ioException.addSuppressed(suppressed);
                    } else {
                        e.addSuppressed(suppressed);
                    }
                }
            }
            throw e;
        }
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new IOException("Failed to close record iterator", e);
            }
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

    static void printRow(CSVPrinter printer, Map<String, Object> row, List<String> headers) throws IOException {
        List<String> values = new ArrayList<>(headers.size());
        for (String header : headers) {
            Object value = resolveValue(row, header);
            values.add(value == null ? "" : String.valueOf(value));
        }
        printer.printRecord(values);
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
