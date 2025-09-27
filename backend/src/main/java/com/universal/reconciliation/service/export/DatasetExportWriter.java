package com.universal.reconciliation.service.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.enums.ExportFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Serialises dataset rows into various export formats.
 */
@Component
public class DatasetExportWriter {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ENGLISH).withZone(SGT);

    private final ObjectMapper objectMapper;

    public DatasetExportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] write(
            ExportFormat format,
            List<DatasetRow> rows,
            List<String> attributeKeys,
            Map<String, Object> metadata) {
        return switch (format) {
            case CSV -> writeCsv(rows, attributeKeys, metadata);
            case JSONL -> writeJsonl(rows, attributeKeys, metadata);
            case XLSX -> writeXlsx(rows, attributeKeys, metadata);
            case PDF -> throw new UnsupportedOperationException("PDF generation is not yet supported");
        };
    }

    private byte[] writeCsv(List<DatasetRow> rows, List<String> attributeKeys, Map<String, Object> metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Export generated at ")
                .append(TIMESTAMP_FORMAT.format(Instant.now()))
                .append(" SGT\n");
        builder.append("# Filters: ")
                .append(metadata.getOrDefault("filterSummary", "{}"))
                .append("\n");

        List<String> header = new ArrayList<>(List.of(
                "Break ID",
                "Run ID",
                "Run Time (SGT)",
                "Trigger Type",
                "Status",
                "Break Type",
                "Detected At (SGT)",
                "Maker",
                "Checker",
                "Latest Comment",
                "Missing Sources",
                "Submitted By",
                "Submitted At (SGT)"));
        header.addAll(attributeKeys.stream().map(this::formatHeader).toList());
        builder.append(String.join(",", header)).append('\n');

        for (DatasetRow row : rows) {
            List<String> values = new ArrayList<>();
            values.add(toCsv(row.breakId()));
            values.add(toCsv(row.runId()));
            values.add(toCsv(row.runDateTime() != null ? TIMESTAMP_FORMAT.format(row.runDateTime()) : null));
            values.add(toCsv(row.triggerType() != null ? row.triggerType().name() : null));
            values.add(toCsv(row.status() != null ? row.status().name() : null));
            values.add(toCsv(row.breakType()));
            values.add(toCsv(row.detectedAt() != null ? TIMESTAMP_FORMAT.format(row.detectedAt()) : null));
            values.add(toCsv(row.maker()));
            values.add(toCsv(row.checker()));
            values.add(toCsv(row.latestComment()));
            values.add(toCsv(String.join(" | ", row.missingSources() != null ? row.missingSources() : List.of())));
            values.add(toCsv(row.submittedBy()));
            values.add(toCsv(row.submittedAt() != null ? TIMESTAMP_FORMAT.format(row.submittedAt()) : null));
            for (String key : attributeKeys) {
                values.add(toCsv(row.attributes().getOrDefault(key, "")));
            }
            builder.append(String.join(",", values)).append('\n');
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] writeJsonl(List<DatasetRow> rows, List<String> attributeKeys, Map<String, Object> metadata) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            for (DatasetRow row : rows) {
                Map<String, Object> payload = toJsonObject(row, attributeKeys, metadata);
                writer.write(objectMapper.writeValueAsString(payload));
                writer.write('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate JSONL export", e);
        }
        return out.toByteArray();
    }

    private byte[] writeXlsx(List<DatasetRow> rows, List<String> attributeKeys, Map<String, Object> metadata) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summary = workbook.createSheet("Summary");
            int rowIdx = 0;
            rowIdx = writeSummaryRow(summary, rowIdx, "Generated At", TIMESTAMP_FORMAT.format(Instant.now()));
            rowIdx = writeSummaryRow(summary, rowIdx, "Filters", Objects.toString(metadata.get("filterSummary"), "{}"));
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            Sheet sheet = workbook.createSheet("Dataset");
            Row headerRow = sheet.createRow(0);
            List<String> header = new ArrayList<>(List.of(
                    "Break ID",
                    "Run ID",
                    "Run Time (SGT)",
                    "Trigger Type",
                    "Status",
                    "Break Type",
                    "Detected At (SGT)",
                    "Maker",
                    "Checker",
                    "Latest Comment",
                    "Missing Sources",
                    "Submitted By",
                    "Submitted At (SGT)"));
            header.addAll(attributeKeys.stream().map(this::formatHeader).toList());
            for (int i = 0; i < header.size(); i++) {
                headerRow.createCell(i).setCellValue(header.get(i));
            }

            int rowNumber = 1;
            for (DatasetRow row : rows) {
                Row excelRow = sheet.createRow(rowNumber++);
                int column = 0;
                setCell(excelRow, column++, row.breakId());
                setCell(excelRow, column++, row.runId());
                setCell(excelRow, column++, row.runDateTime() != null ? TIMESTAMP_FORMAT.format(row.runDateTime()) : null);
                setCell(excelRow, column++, row.triggerType() != null ? row.triggerType().name() : null);
                setCell(excelRow, column++, row.status() != null ? row.status().name() : null);
                setCell(excelRow, column++, row.breakType());
                setCell(excelRow, column++, row.detectedAt() != null ? TIMESTAMP_FORMAT.format(row.detectedAt()) : null);
                setCell(excelRow, column++, row.maker());
                setCell(excelRow, column++, row.checker());
                setCell(excelRow, column++, row.latestComment());
                setCell(excelRow, column++, String.join(" | ", row.missingSources() != null ? row.missingSources() : List.of()));
                setCell(excelRow, column++, row.submittedBy());
                setCell(excelRow, column++, row.submittedAt() != null ? TIMESTAMP_FORMAT.format(row.submittedAt()) : null);
                for (String key : attributeKeys) {
                    setCell(excelRow, column++, row.attributes().getOrDefault(key, ""));
                }
            }

            for (int i = 0; i < header.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate XLSX export", e);
        }
    }

    private Map<String, Object> toJsonObject(DatasetRow row, List<String> attributeKeys, Map<String, Object> metadata)
            throws JsonProcessingException {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("breakId", row.breakId());
        payload.put("runId", row.runId());
        payload.put("runTime", row.runDateTime() != null ? TIMESTAMP_FORMAT.format(row.runDateTime()) : null);
        payload.put("triggerType", row.triggerType() != null ? row.triggerType().name() : null);
        payload.put("status", row.status() != null ? row.status().name() : null);
        payload.put("breakType", row.breakType());
        payload.put("detectedAt", row.detectedAt() != null ? TIMESTAMP_FORMAT.format(row.detectedAt()) : null);
        payload.put("maker", row.maker());
        payload.put("checker", row.checker());
        payload.put("latestComment", row.latestComment());
        payload.put("missingSources", row.missingSources());
        payload.put("submittedBy", row.submittedBy());
        payload.put("submittedAt", row.submittedAt() != null ? TIMESTAMP_FORMAT.format(row.submittedAt()) : null);
        payload.put("attributes", attributeKeys.stream()
                .collect(Collectors.toMap(key -> key, key -> row.attributes().getOrDefault(key, ""))));
        payload.put("metadata", metadata);
        return payload;
    }

    private int writeSummaryRow(Sheet sheet, int index, String label, Object value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value.toString() : "");
        return index + 1;
    }

    private String formatHeader(String key) {
        if (key == null) {
            return "";
        }
        String spaced = key.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private String toCsv(Object value) {
        if (value == null) {
            return "";
        }
        String string = value.toString();
        if (string.contains(",") || string.contains("\"") || string.contains("\n")) {
            string = string.replace("\"", "\"\"");
            return "\"" + string + "\"";
        }
        return string;
    }

    private void setCell(Row row, int column, Object value) {
        Cell cell = row.createCell(column);
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    public static List<String> normaliseAttributeKeys(Set<String> attributes) {
        return attributes.stream().sorted().toList();
    }
}
