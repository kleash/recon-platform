package com.universal.reconciliation.service.transform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.universal.reconciliation.domain.dto.admin.TransformationFilePreviewUploadRequest;
import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parses ad-hoc sample files uploaded by administrators so they can preview
 * transformation behaviour without ingesting a full batch first.
 */
@Service
public class TransformationSampleFileService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int MAX_RECORDS = 10;

    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;
    private final long maxUploadBytes;

    public TransformationSampleFileService(
            ObjectMapper objectMapper,
            @Value("${admin.transformations.preview.max-upload-bytes:2097152}") long maxUploadBytes) {
        this.objectMapper = objectMapper;
        this.xmlMapper = XmlMapper.builder()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .build();
        this.maxUploadBytes = maxUploadBytes;
    }

    public List<Map<String, Object>> parseSamples(
            TransformationFilePreviewUploadRequest request, MultipartFile file) {
        Objects.requireNonNull(file, "Sample file is required");
        enforceUploadSize(file);

        int limit = Math.min(Math.max(Optional.ofNullable(request.limit()).orElse(MAX_RECORDS), 1), MAX_RECORDS);
        TransformationSampleFileType fileType = request.fileType();
        if (fileType == null) {
            throw new TransformationEvaluationException("File type is required for sample preview");
        }
        return switch (fileType) {
            case CSV -> parseDelimitedFile(file, request, limit, ',');
            case DELIMITED -> parseDelimitedFile(file, request, limit, '\t');
            case EXCEL -> parseExcelFile(file, request, limit);
            case JSON -> parseJsonFile(file, request, limit);
            case XML -> parseXmlFile(file, request, limit);
        };
    }

    private void enforceUploadSize(MultipartFile file) {
        long size = file.getSize();
        if (size > maxUploadBytes) {
            throw new TransformationEvaluationException(
                    "Sample file exceeds the configured upload limit of " + maxUploadBytes + " bytes.");
        }
    }

    private List<Map<String, Object>> parseDelimitedFile(
            MultipartFile file, TransformationFilePreviewUploadRequest request, int limit, char defaultDelimiter) {
        char delimiter = resolveDelimiter(request.delimiter(), defaultDelimiter);
        Charset charset = resolveCharset(request.encoding());
        boolean hasHeader = request.hasHeader();
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder()
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setDelimiter(delimiter);
        if (hasHeader) {
            builder.setHeader();
            builder.setSkipHeaderRecord(true);
        }
        CSVFormat format = builder.build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), charset);
                CSVParser parser = new CSVParser(reader, format)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> headers = new ArrayList<>();
            if (hasHeader) {
                headers.addAll(parser.getHeaderNames());
                if (headers.isEmpty()) {
                    throw new TransformationEvaluationException(
                            "Header row was expected but none was found in the sample file.");
                }
            }
            int processed = 0;
            for (CSVRecord record : parser) {
                if (!hasHeader && headers.isEmpty()) {
                    headers = generateColumnHeaders(record.size());
                } else {
                    expandHeaders(headers, record.size());
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.size() && index < record.size(); index++) {
                    row.put(headers.get(index), record.get(index));
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                    processed++;
                }
                if (processed >= limit) {
                    break;
                }
            }
            return rows;
        } catch (IOException ex) {
            throw new TransformationEvaluationException("Failed to read delimited sample file", ex);
        }
    }

    private List<Map<String, Object>> parseExcelFile(
            MultipartFile file, TransformationFilePreviewUploadRequest request, int limit) {
        boolean hasHeader = request.hasHeader();
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook, request.sheetName());
            if (sheet == null) {
                throw new TransformationEvaluationException("Unable to locate the requested sheet in the workbook");
            }
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> headers = new ArrayList<>();
            int processed = 0;
            Iterator<Row> iterator = sheet.iterator();
            while (iterator.hasNext()) {
                Row current = iterator.next();
                if (current == null) {
                    continue;
                }
                if (hasHeader && headers.isEmpty()) {
                    headers = extractExcelHeaders(current, formatter, evaluator);
                    continue;
                }
                if (!hasHeader && headers.isEmpty()) {
                    headers = generateColumnHeaders(detectExcelColumnCount(current));
                } else {
                    expandHeaders(headers, detectExcelColumnCount(current));
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = current.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell == null) {
                        continue;
                    }
                    row.put(headers.get(i), readExcelCell(cell, formatter, evaluator));
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                    processed++;
                }
                if (processed >= limit) {
                    break;
                }
            }
            return rows;
        } catch (IOException ex) {
            throw new TransformationEvaluationException("Failed to read Excel sample file", ex);
        }
    }

    private List<Map<String, Object>> parseJsonFile(
            MultipartFile file, TransformationFilePreviewUploadRequest request, int limit) {
        JsonNode root = readJsonTree(file, request.encoding());
        JsonNode target = resolvePath(root, request.recordPath());
        List<Map<String, Object>> rows = new ArrayList<>();
        Iterable<JsonNode> sources = target.isArray() ? target : List.of(target);
        int processed = 0;
        for (JsonNode node : sources) {
            if (node == null || node.isNull()) {
                continue;
            }
            rows.add(objectMapper.convertValue(node, MAP_TYPE));
            processed++;
            if (processed >= limit) {
                break;
            }
        }
        return rows;
    }

    private List<Map<String, Object>> parseXmlFile(
            MultipartFile file, TransformationFilePreviewUploadRequest request, int limit) {
        JsonNode root = readXmlTree(file, request.encoding());
        JsonNode target = resolvePath(root, request.recordPath());
        List<Map<String, Object>> rows = new ArrayList<>();
        Iterable<JsonNode> sources = target.isArray() ? target : List.of(target);
        int processed = 0;
        for (JsonNode node : sources) {
            if (node == null || node.isNull()) {
                continue;
            }
            rows.add(objectMapper.convertValue(node, MAP_TYPE));
            processed++;
            if (processed >= limit) {
                break;
            }
        }
        return rows;
    }

    private Charset resolveCharset(String encoding) {
        if (!StringUtils.hasText(encoding)) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ex) {
            throw new TransformationEvaluationException("Unsupported character encoding: " + encoding, ex);
        }
    }

    private char resolveDelimiter(String delimiter, char defaultDelimiter) {
        if (!StringUtils.hasLength(delimiter)) {
            return defaultDelimiter;
        }
        return delimiter.charAt(0);
    }

    private List<String> generateColumnHeaders(int count) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            headers.add("COLUMN_" + (i + 1));
        }
        return headers;
    }

    private void expandHeaders(List<String> headers, int desiredSize) {
        if (headers == null) {
            return;
        }
        int target = Math.max(desiredSize, headers.size());
        while (headers.size() < target) {
            headers.add("COLUMN_" + (headers.size() + 1));
        }
    }

    private List<String> extractExcelHeaders(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> headers = new ArrayList<>();
        int columnCount = detectExcelColumnCount(row);
        for (int i = 0; i < columnCount; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                headers.add("COLUMN_" + (i + 1));
            } else {
                String header = formatter.formatCellValue(cell, evaluator);
                headers.add(StringUtils.hasText(header) ? header : "COLUMN_" + (i + 1));
            }
        }
        return headers;
    }

    private Object readExcelCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell evaluated = evaluator.evaluateInCell(cell);
        CellType type = evaluated.getCellType();
        return switch (type) {
            case BOOLEAN -> evaluated.getBooleanCellValue();
            case NUMERIC -> org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(evaluated)
                    ? evaluated.getLocalDateTimeCellValue()
                    : evaluated.getNumericCellValue();
            case STRING -> evaluated.getStringCellValue();
            case BLANK -> null;
            case ERROR -> formatExcelError(evaluated);
            case FORMULA -> formatter.formatCellValue(evaluated, evaluator);
            default -> formatter.formatCellValue(evaluated, evaluator);
        };
    }

    private String formatExcelError(Cell cell) {
        try {
            return "Excel Cell Error: " + FormulaError.forInt(cell.getErrorCellValue()).getString();
        } catch (IllegalArgumentException ex) {
            return "Excel Cell Error (code " + cell.getErrorCellValue() + ")";
        }
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName) {
        if (StringUtils.hasText(sheetName)) {
            return workbook.getSheet(sheetName);
        }
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    private int detectExcelColumnCount(Row row) {
        if (row == null) {
            return 0;
        }
        int lastCell = row.getLastCellNum();
        if (lastCell < 0) {
            lastCell = 0;
        }
        int physical = row.getPhysicalNumberOfCells();
        return Math.max(lastCell, physical);
    }

    private JsonNode readJsonTree(MultipartFile file, String encoding) {
        try {
            byte[] bytes = file.getBytes();
            String payload = new String(bytes, resolveCharset(encoding));
            return objectMapper.readTree(payload);
        } catch (IOException ex) {
            throw new TransformationEvaluationException("Failed to parse JSON sample payload", ex);
        }
    }

    private JsonNode readXmlTree(MultipartFile file, String encoding) {
        try {
            byte[] bytes = file.getBytes();
            String payload = new String(bytes, resolveCharset(encoding));
            return xmlMapper.readTree(payload);
        } catch (IOException ex) {
            throw new TransformationEvaluationException("Failed to parse XML sample payload", ex);
        }
    }

    private JsonNode resolvePath(JsonNode root, String rawPath) {
        if (root == null) {
            return null;
        }
        if (!StringUtils.hasText(rawPath)) {
            return root;
        }
        String pointer = toJsonPointer(rawPath);
        JsonNode target = root.at(pointer);
        if (target.isMissingNode() || target.isNull()) {
            throw new TransformationEvaluationException("No data found at record path '" + rawPath + "'.");
        }
        return target;
    }

    private String toJsonPointer(String rawPath) {
        String path = rawPath.trim();
        if (path.isEmpty()) {
            return "";
        }
        StringBuilder pointer = new StringBuilder();
        int index = 0;
        while (index < path.length()) {
            char current = path.charAt(index);
            if (current == '.') {
                index++;
                continue;
            }
            if (current == '[') {
                int closing = path.indexOf(']', index);
                if (closing <= index + 1) {
                    throw new TransformationEvaluationException(
                            "Invalid array index in record path '" + rawPath + "'.");
                }
                String digits = path.substring(index + 1, closing).trim();
                try {
                    Integer.parseInt(digits);
                } catch (NumberFormatException ex) {
                    throw new TransformationEvaluationException(
                            "Array index must be numeric in record path '" + rawPath + "'.", ex);
                }
                pointer.append('/').append(digits);
                index = closing + 1;
                continue;
            }
            int start = index;
            while (index < path.length()
                    && path.charAt(index) != '.'
                    && path.charAt(index) != '[') {
                index++;
            }
            String key = path.substring(start, index).trim();
            if (!key.isEmpty()) {
                pointer.append('/').append(escapeJsonPointerSegment(key));
            }
        }
        return pointer.toString();
    }

    private String escapeJsonPointerSegment(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
