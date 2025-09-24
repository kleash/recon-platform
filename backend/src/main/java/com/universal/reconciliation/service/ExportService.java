package com.universal.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.RunAnalyticsDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.entity.ReportColumn;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.repository.ReportTemplateRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Translates reconciliation runs into configurable Excel workbooks.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ObjectMapper objectMapper;
    private final ReportTemplateRepository templateRepository;

    public ExportService(ObjectMapper objectMapper, ReportTemplateRepository templateRepository) {
        this.objectMapper = objectMapper;
        this.templateRepository = templateRepository;
    }

    public byte[] exportToExcel(RunDetailDto detail) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ReportTemplate template = resolveTemplate(detail);
            List<ColumnLayout> columnLayouts = toColumnLayouts(template);
            CellStyle highlightStyle = createHighlightStyle(workbook);

            populateSummarySheet(detail, template, workbook.createSheet("Summary"));
            if (template.isIncludeMatched()) {
                populateMatchedSheet(detail, workbook.createSheet("Matched"));
            }
            if (template.isIncludeMismatched()) {
                populateBreakSheet(
                        detail,
                        workbook.createSheet("Mismatched"),
                        columnLayouts,
                        highlightStyle,
                        Set.of(BreakType.MISMATCH));
            }
            if (template.isIncludeMissing()) {
                populateBreakSheet(
                        detail,
                        workbook.createSheet("Missing"),
                        columnLayouts,
                        highlightStyle,
                        Set.of(
                                BreakType.MISSING_IN_SOURCE_A,
                                BreakType.MISSING_IN_SOURCE_B,
                                BreakType.SOURCE_MISSING,
                                BreakType.ANCHOR_MISSING));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
        }
    }

    private void populateSummarySheet(RunDetailDto detail, ReportTemplate template, Sheet sheet) {
        int rowIdx = 0;
        rowIdx = writeSummaryRow(sheet, rowIdx, "Definition ID", detail.summary().definitionId());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Run ID", detail.summary().runId());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Run Date", detail.summary().runDateTime());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Trigger Type", detail.summary().triggerType());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Triggered By", detail.summary().triggeredBy());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Correlation Id", detail.summary().triggerCorrelationId());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Trigger Comments", detail.summary().triggerComments());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Matched Records", detail.summary().matched());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Mismatched Records", detail.summary().mismatched());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Missing Records", detail.summary().missing());

        RunAnalyticsDto analytics = detail.analytics();
        rowIdx = writeSummaryRow(sheet, rowIdx, "Filtered Breaks", analytics.filteredBreakCount());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Total Breaks", analytics.totalBreakCount());
        rowIdx = writeSummaryRow(sheet, rowIdx, "Template", template.getName());

        rowIdx += 2;
        rowIdx = writeDistribution(sheet, rowIdx, "Breaks by Status", analytics.breaksByStatus());
        rowIdx += 2;
        rowIdx = writeDistribution(sheet, rowIdx, "Breaks by Product", analytics.breaksByProduct());
        rowIdx += 2;
        writeDistribution(sheet, rowIdx, "Open Break Age Buckets", analytics.openBreaksByAgeBucket());

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void populateMatchedSheet(RunDetailDto detail, Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Metric");
        header.createCell(1).setCellValue("Value");

        sheet.createRow(1).createCell(0).setCellValue("Matched Records");
        sheet.getRow(1).createCell(1).setCellValue(detail.summary().matched());

        sheet.createRow(2).createCell(0).setCellValue("Total Breaks");
        sheet.getRow(2).createCell(1).setCellValue(detail.analytics().totalBreakCount());

        sheet.createRow(3).createCell(0).setCellValue("Coverage %");
        int denominator = detail.analytics().totalBreakCount() + detail.summary().matched();
        double coverage = denominator == 0 ? 0.0 : (double) detail.summary().matched() / denominator;
        sheet.getRow(3).createCell(1).setCellValue(coverage);

        int rowIdx = sheet.getLastRowNum() + 1;
        rowIdx++; // leave a blank row between summary metrics and the product list for readability
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Top Break Products");
        for (Map.Entry<String, Long> entry : detail.analytics().breaksByProduct().entrySet().stream()
                .limit(5)
                .toList()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void populateBreakSheet(
            RunDetailDto detail,
            Sheet sheet,
            List<ColumnLayout> columnLayouts,
            CellStyle highlightStyle,
            Set<BreakType> includedTypes) {
        List<String> baseHeaders = List.of(
                "Break ID",
                "Break Type",
                "Status",
                "Detected At",
                "Product",
                "Sub-product",
                "Entity",
                "Comments");

        Row header = sheet.createRow(0);
        int columnIndex = 0;
        for (String baseHeader : baseHeaders) {
            header.createCell(columnIndex++).setCellValue(baseHeader);
        }
        for (ColumnLayout layout : columnLayouts) {
            header.createCell(columnIndex++).setCellValue(layout.header());
        }

        int rowIdx = 1;
        for (BreakItemDto item : detail.breaks()) {
            if (!includedTypes.contains(item.breakType())) {
                continue;
            }
            Row row = sheet.createRow(rowIdx++);
            int idx = 0;
            row.createCell(idx++).setCellValue(item.id());
            row.createCell(idx++).setCellValue(item.breakType().name());
            row.createCell(idx++).setCellValue(item.status().name());
            row.createCell(idx++).setCellValue(item.detectedAt() != null ? item.detectedAt().toString() : "");
            row.createCell(idx++).setCellValue(classificationValue(item, "product"));
            row.createCell(idx++).setCellValue(classificationValue(item, "subProduct"));
            row.createCell(idx++).setCellValue(classificationValue(item, "entity"));
            row.createCell(idx++)
                    .setCellValue(item.comments().stream()
                            .map(comment -> String.format("%s: %s", comment.actorDn(), comment.comment()))
                            .collect(Collectors.joining(" | ")));

            for (ColumnLayout layout : columnLayouts) {
                Cell cell = row.createCell(idx++);
                Object value = resolveValue(layout, item);
                setCellValue(cell, value);
                if (layout.highlightDifferences()
                        && (layout.source() == ReportColumnSource.SOURCE_A
                                || layout.source() == ReportColumnSource.SOURCE_B)
                        && fieldDiffers(layout.sourceField(), item)) {
                    cell.setCellStyle(highlightStyle);
                }
            }
        }

        for (int i = 0; i < columnIndex; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private ReportTemplate resolveTemplate(RunDetailDto detail) {
        return templateRepository
                .findTopByDefinitionIdOrderById(detail.summary().definitionId())
                .orElseGet(() -> defaultTemplate());
    }

    private ReportTemplate defaultTemplate() {
        ReportTemplate template = new ReportTemplate();
        template.setName("Default Layout");
        template.setDescription("Automatically generated layout");
        template.setIncludeMatched(true);
        template.setIncludeMismatched(true);
        template.setIncludeMissing(true);
        template.setHighlightDifferences(true);
        return template;
    }

    private List<ColumnLayout> toColumnLayouts(ReportTemplate template) {
        if (template.getColumns() == null || template.getColumns().isEmpty()) {
            return List.of(
                    new ColumnLayout("Source A", ReportColumnSource.SOURCE_A, null, template.isHighlightDifferences()),
                    new ColumnLayout("Source B", ReportColumnSource.SOURCE_B, null, template.isHighlightDifferences()));
        }
        return template.getColumns().stream()
                .sorted(Comparator.comparingInt(ReportColumn::getDisplayOrder))
                .map(column -> new ColumnLayout(
                        column.getHeader(), column.getSource(), column.getSourceField(), column.isHighlightDifferences()))
                .toList();
    }

    private int writeSummaryRow(Sheet sheet, int rowIdx, String label, Object value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        setCellValue(row.createCell(1), value);
        return rowIdx + 1;
    }

    private int writeDistribution(Sheet sheet, int startRow, String title, Map<String, Long> distribution) {
        Row titleRow = sheet.createRow(startRow);
        titleRow.createCell(0).setCellValue(title);
        int rowIdx = startRow + 1;
        for (Map.Entry<String, Long> entry : distribution.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        return rowIdx;
    }

    private CellStyle createHighlightStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private boolean fieldDiffers(String field, BreakItemDto item) {
        if (field == null) {
            return false;
        }
        Map<String, Object> anchor = anchorPayload(item);
        List<Map<String, Object>> peers = peerPayloads(item);
        Object left = anchor.get(field);
        for (Map<String, Object> peer : peers) {
            Object right = peer.get(field);
            if (!valuesEqual(left, right)) {
                return true;
            }
        }
        return false;
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return numbersEqual(leftNumber, rightNumber);
        }
        return Objects.equals(stringify(left), stringify(right));
    }

    private boolean numbersEqual(Number left, Number right) {
        try {
            BigDecimal leftDecimal = toBigDecimal(left);
            BigDecimal rightDecimal = toBigDecimal(right);
            return leftDecimal.compareTo(rightDecimal) == 0;
        } catch (NumberFormatException ex) {
            return Double.compare(left.doubleValue(), right.doubleValue()) == 0;
        }
    }

    private BigDecimal toBigDecimal(Number value) {
        return value instanceof BigDecimal bigDecimal ? bigDecimal : new BigDecimal(value.toString());
    }

    private Object resolveValue(ColumnLayout layout, BreakItemDto item) {
        return switch (layout.source()) {
            case SOURCE_A -> layout.sourceField() == null
                    ? writeJson(anchorPayload(item))
                    : anchorPayload(item).getOrDefault(layout.sourceField(), "");
            case SOURCE_B -> layout.sourceField() == null
                    ? writeJson(peerPayloads(item))
                    : resolvePeerValue(layout.sourceField(), item);
            case BREAK_METADATA -> resolveMetadata(layout.sourceField(), item);
        };
    }

    private Object resolveMetadata(String field, BreakItemDto item) {
        if (field == null) {
            return "";
        }
        return switch (field) {
            case "id" -> item.id();
            case "status" -> item.status().name();
            case "breakType" -> item.breakType().name();
            case "product" -> classificationValue(item, "product");
            case "subProduct" -> classificationValue(item, "subProduct");
            case "entity" -> classificationValue(item, "entity");
            case "missingSources" -> String.join(", ", item.missingSources());
            case "detectedAt" -> item.detectedAt() != null ? item.detectedAt().toString() : "";
            case "comments" -> item.comments().stream()
                    .map(comment -> String.format("%s: %s", comment.actorDn(), comment.comment()))
                    .collect(Collectors.joining(" | "));
            default -> {
                log.warn("Unknown metadata field requested for export: {}", field);
                yield "";
            }
        };
    }

    private String classificationValue(BreakItemDto item, String key) {
        return item.classifications().getOrDefault(key, "");
    }

    private Map<String, Object> anchorPayload(BreakItemDto item) {
        return item.sources().values().stream().findFirst().orElse(Map.of());
    }

    private List<Map<String, Object>> peerPayloads(BreakItemDto item) {
        return item.sources().values().stream().skip(1).toList();
    }

    private Object resolvePeerValue(String field, BreakItemDto item) {
        List<Map<String, Object>> peers = peerPayloads(item);
        if (peers.isEmpty()) {
            return "";
        }
        if (peers.size() == 1) {
            return peers.get(0).getOrDefault(field, "");
        }
        return peers.stream()
                .map(payload -> Objects.toString(payload.get(field), ""))
                .collect(Collectors.joining(" | "));
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize break payload for export", e);
            throw new IllegalStateException("Failed to serialize break payload for export", e);
        }
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof TemporalAccessor temporalAccessor) {
            cell.setCellValue(temporalAccessor.toString());
        } else {
            cell.setCellValue(stringify(value));
        }
    }

    private String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    private record ColumnLayout(
            String header, ReportColumnSource source, String sourceField, boolean highlightDifferences) {}
}
