package com.universal.reconciliation.service.ingestion;

import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Excel ingestion adapter capable of reading one or many worksheets while honouring
 * the same parsing options exposed by the admin configurator preview pipeline.
 */
@Component
public class ExcelIngestionAdapter implements IngestionAdapter {

    static final String OPTION_HAS_HEADER = "hasHeader";
    static final String OPTION_HEADER = "header";
    static final String OPTION_SHEET_NAME = "sheetName";
    static final String OPTION_SHEET_NAMES = "sheetNames";
    static final String OPTION_INCLUDE_ALL_SHEETS = "includeAllSheets";
    static final String OPTION_INCLUDE_SHEET_COLUMN = "includeSheetNameColumn";
    static final String OPTION_SHEET_COLUMN_NAME = "sheetNameColumn";
    static final String OPTION_SKIP_ROWS = "skipRows";
    static final String DEFAULT_SHEET_COLUMN = "_sheet";

    @Override
    public IngestionAdapterType getType() {
        return IngestionAdapterType.EXCEL_FILE;
    }

    @Override
    public List<Map<String, Object>> readRecords(IngestionAdapterRequest request) {
        try (InputStream stream = request.inputStreamSupplier().get();
                Workbook workbook = WorkbookFactory.create(stream)) {
            ExcelParsingOptions options = parseOptions(request.options());
            List<Sheet> sheets = resolveSheets(workbook, options);
            if (sheets.isEmpty()) {
                throw new TransformationEvaluationException("No worksheets matched the configured criteria");
            }
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Sheet sheet : sheets) {
                rows.addAll(readSheet(sheet, options, formatter, evaluator));
            }
            return rows;
        } catch (IOException ex) {
            throw new TransformationEvaluationException("Failed to read Excel payload", ex);
        }
    }

    private List<Map<String, Object>> readSheet(
            Sheet sheet, ExcelParsingOptions options, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<Map<String, Object>> records = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        int skipped = 0;
        for (Row row : sheet) {
            if (row == null) {
                continue;
            }
            if (options.hasHeader() && headers.isEmpty()) {
                headers = extractHeaders(row, formatter, evaluator);
                continue;
            }
            if (skipped < options.skipRows()) {
                skipped++;
                continue;
            }
            if (!options.hasHeader() && headers.isEmpty()) {
                headers = generateHeaders(detectColumnCount(row));
            } else {
                expandHeaders(headers, detectColumnCount(row));
            }
            Map<String, Object> record = extractRow(row, headers, formatter, evaluator);
            if (record.isEmpty()) {
                continue;
            }
            if (options.includeSheetColumn()) {
                record.put(options.sheetColumnName(), sheet.getSheetName());
            }
            records.add(record);
        }
        return records;
    }

    private Map<String, Object> extractRow(
            Row row, List<String> headers, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Object> record = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index);
            if (!StringUtils.hasText(header)) {
                continue;
            }
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                continue;
            }
            Cell evaluated = evaluator.evaluateInCell(cell);
            Object value = readCellValue(evaluated, formatter);
            if (value != null) {
                record.put(header, value);
            }
        }
        return record;
    }

    private Object readCellValue(Cell cell, DataFormatter formatter) {
        CellType type = cell.getCellType();
        return switch (type) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> formatter.formatCellValue(cell);
            case STRING -> {
                String text = formatter.formatCellValue(cell);
                yield StringUtils.hasText(text) ? text : null;
            }
            case BLANK -> null;
            default -> {
                String text = formatter.formatCellValue(cell);
                yield StringUtils.hasText(text) ? text : null;
            }
        };
    }

    private List<String> extractHeaders(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        int columnCount = detectColumnCount(row);
        List<String> headers = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                headers.add("COLUMN_" + (index + 1));
                continue;
            }
            Cell evaluated = evaluator.evaluateInCell(cell);
            String header = formatter.formatCellValue(evaluated);
            headers.add(StringUtils.hasText(header) ? header : "COLUMN_" + (index + 1));
        }
        return headers;
    }

    private int detectColumnCount(Row row) {
        int lastIndex = row.getLastCellNum();
        return Math.max(lastIndex, 0);
    }

    private List<String> generateHeaders(int count) {
        List<String> headers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            headers.add("COLUMN_" + (index + 1));
        }
        return headers;
    }

    private void expandHeaders(List<String> headers, int desiredSize) {
        int current = headers.size();
        if (desiredSize <= current) {
            return;
        }
        for (int index = current; index < desiredSize; index++) {
            headers.add("COLUMN_" + (index + 1));
        }
    }

    private ExcelParsingOptions parseOptions(Map<String, Object> rawOptions) {
        Map<String, Object> options = rawOptions == null ? Map.of() : rawOptions;
        boolean hasHeader = toBoolean(options.getOrDefault(OPTION_HAS_HEADER, options.get(OPTION_HEADER)), true);
        boolean includeAllSheets = toBoolean(options.get(OPTION_INCLUDE_ALL_SHEETS), false);
        boolean includeSheetColumn = toBoolean(options.get(OPTION_INCLUDE_SHEET_COLUMN), false);
        String sheetColumn = toString(options.get(OPTION_SHEET_COLUMN_NAME));
        if (!StringUtils.hasText(sheetColumn)) {
            sheetColumn = DEFAULT_SHEET_COLUMN;
        }
        int skipRows = toInteger(options.get(OPTION_SKIP_ROWS), 0);
        List<String> sheetNames = toStringList(options.get(OPTION_SHEET_NAMES));
        String sheetName = toString(options.get(OPTION_SHEET_NAME));
        return new ExcelParsingOptions(
                hasHeader,
                includeAllSheets,
                includeSheetColumn,
                sheetColumn,
                skipRows,
                sheetName,
                sheetNames);
    }

    private List<Sheet> resolveSheets(Workbook workbook, ExcelParsingOptions options) {
        int total = workbook.getNumberOfSheets();
        List<Sheet> resolved = new ArrayList<>();
        if (total <= 0) {
            return resolved;
        }
        if (options.includeAllSheets()) {
            for (int index = 0; index < total; index++) {
                resolved.add(workbook.getSheetAt(index));
            }
            return resolved;
        }
        if (!options.sheetNames().isEmpty()) {
            Map<String, Sheet> lookup = new LinkedHashMap<>();
            for (int index = 0; index < total; index++) {
                Sheet sheet = workbook.getSheetAt(index);
                lookup.put(sheet.getSheetName().trim().toLowerCase(Locale.ENGLISH), sheet);
            }
            for (String requested : options.sheetNames()) {
                if (!StringUtils.hasText(requested)) {
                    continue;
                }
                Sheet matched = lookup.get(requested.trim().toLowerCase(Locale.ENGLISH));
                if (matched != null && !resolved.contains(matched)) {
                    resolved.add(matched);
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        if (StringUtils.hasText(options.sheetName())) {
            Sheet matched = workbook.getSheet(options.sheetName());
            if (matched != null) {
                resolved.add(matched);
                return resolved;
            }
        }
        resolved.add(workbook.getSheetAt(0));
        return resolved;
    }

    private boolean toBoolean(Object candidate, boolean defaultValue) {
        if (candidate == null) {
            return defaultValue;
        }
        if (candidate instanceof Boolean bool) {
            return bool;
        }
        if (candidate instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }

    private int toInteger(Object candidate, int defaultValue) {
        if (candidate == null) {
            return defaultValue;
        }
        if (candidate instanceof Number number) {
            return number.intValue();
        }
        if (candidate instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String toString(Object candidate) {
        if (candidate == null) {
            return null;
        }
        String value = String.valueOf(candidate);
        return StringUtils.hasText(value) ? value : null;
    }

    private List<String> toStringList(Object candidate) {
        if (candidate == null) {
            return List.of();
        }
        if (candidate instanceof String single) {
            if (!StringUtils.hasText(single)) {
                return List.of();
            }
            return List.of(single.trim());
        }
        if (candidate instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        }
        return List.of(candidate.toString().trim());
    }

    private record ExcelParsingOptions(
            boolean hasHeader,
            boolean includeAllSheets,
            boolean includeSheetColumn,
            String sheetColumnName,
            int skipRows,
            String sheetName,
            List<String> sheetNames) {

        ExcelParsingOptions {
            sheetNames = sheetNames == null ? List.of() : List.copyOf(sheetNames);
        }
    }
}
