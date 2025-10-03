package com.universal.reconciliation.service.admin;

import com.universal.reconciliation.domain.dto.admin.AdminSourceSchemaFieldDto;
import com.universal.reconciliation.domain.dto.admin.SourceSchemaInferenceRequest;
import com.universal.reconciliation.domain.dto.admin.SourceSchemaInferenceResponse;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationPreviewUploadRequest;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import com.universal.reconciliation.service.transform.TransformationSampleFileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Provides schema inference helpers for administrators defining source metadata.
 */
@Service
public class AdminSourceSchemaService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));

    private final TransformationSampleFileService sampleFileService;

    public AdminSourceSchemaService(TransformationSampleFileService sampleFileService) {
        this.sampleFileService = sampleFileService;
    }

    public SourceSchemaInferenceResponse inferSchema(SourceSchemaInferenceRequest request, MultipartFile file) {
        Objects.requireNonNull(request, "Inference request is required");
        Objects.requireNonNull(file, "Sample file is required");

        SourceTransformationPreviewUploadRequest previewRequest =
                toPreviewRequest(request);
        List<Map<String, Object>> rows = sampleFileService.parseSamples(previewRequest, file);

        if (rows.isEmpty()) {
            throw new TransformationEvaluationException("Sample did not contain any rows to infer schema");
        }

        List<AdminSourceSchemaFieldDto> fields = inferFields(rows);
        return new SourceSchemaInferenceResponse(fields, rows);
    }

    private SourceTransformationPreviewUploadRequest toPreviewRequest(SourceSchemaInferenceRequest request) {
        return new SourceTransformationPreviewUploadRequest(
                request.fileType(),
                request.hasHeader(),
                request.delimiter(),
                request.sheetName(),
                Optional.ofNullable(request.sheetNames()).orElse(List.of()),
                request.includeAllSheets(),
                false,
                null,
                request.recordPath(),
                request.encoding(),
                request.limit(),
                request.skipRows(),
                null);
    }

    private List<AdminSourceSchemaFieldDto> inferFields(List<Map<String, Object>> rows) {
        LinkedHashSet<String> columnOrder = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            for (String key : row.keySet()) {
                if (StringUtils.hasText(key)) {
                    columnOrder.add(key.trim());
                }
            }
        }

        List<AdminSourceSchemaFieldDto> fields = new ArrayList<>();
        for (String column : columnOrder) {
            ColumnSummary summary = summariseColumn(column, rows);
            fields.add(new AdminSourceSchemaFieldDto(
                    column,
                    humanise(column),
                    summary.dataType,
                    summary.required,
                    summary.description));
        }
        return fields;
    }

    private ColumnSummary summariseColumn(String column, List<Map<String, Object>> rows) {
        ColumnSummary summary = new ColumnSummary();
        summary.dataType = FieldDataType.STRING;
        summary.required = true;

        for (Map<String, Object> row : rows) {
            if (!row.containsKey(column)) {
                summary.required = false;
                continue;
            }
            Object rawValue = row.get(column);
            if (rawValue == null) {
                summary.required = false;
                continue;
            }
            String value = rawValue.toString().trim();
            if (value.isEmpty()) {
                summary.required = false;
                continue;
            }

            summary.sampleValues.add(value);
            summary.dataType = promoteType(summary.dataType, value);
        }

        if (summary.sampleValues.isEmpty()) {
            summary.required = false;
            summary.description = null;
        } else {
            summary.description = summary.sampleValues.stream().limit(3).collect(java.util.stream.Collectors.joining(", "));
        }
        return summary;
    }

    private FieldDataType promoteType(FieldDataType current, String value) {
        if (looksLikeInteger(value)) {
            if (current == FieldDataType.STRING) {
                return FieldDataType.INTEGER;
            }
            if (current == FieldDataType.INTEGER) {
                return FieldDataType.INTEGER;
            }
            if (current == FieldDataType.DECIMAL) {
                return FieldDataType.DECIMAL;
            }
        }
        if (looksLikeDecimal(value)) {
            if (current == FieldDataType.STRING || current == FieldDataType.INTEGER) {
                return FieldDataType.DECIMAL;
            }
            return current;
        }
        if (looksLikeDate(value)) {
            if (current == FieldDataType.STRING) {
                return FieldDataType.DATE;
            }
            return current;
        }
        return FieldDataType.STRING;
    }

    private boolean looksLikeInteger(String value) {
        try {
            new BigDecimal(value);
            return value.matches("[-+]?\\d+");
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean looksLikeDecimal(String value) {
        try {
            new BigDecimal(value);
            return value.contains(".");
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean looksLikeDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate.parse(value, formatter);
                return true;
            } catch (DateTimeParseException ignored) {
                // continue
            }
        }
        return false;
    }

    private String humanise(String column) {
        if (!StringUtils.hasText(column)) {
            return column;
        }
        String replaced = column
                .replaceAll("[_-]+", " ")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        if (replaced.isEmpty()) {
            return column;
        }
        return Character.toUpperCase(replaced.charAt(0)) + replaced.substring(1);
    }

    private static class ColumnSummary {
        FieldDataType dataType;
        boolean required;
        String description;
        Set<String> sampleValues = new LinkedHashSet<>();
    }
}
