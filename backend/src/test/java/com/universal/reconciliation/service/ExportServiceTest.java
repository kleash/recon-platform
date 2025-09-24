package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakCommentDto;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.FilterMetadataDto;
import com.universal.reconciliation.domain.dto.ReconciliationSummaryDto;
import com.universal.reconciliation.domain.dto.RunAnalyticsDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.domain.entity.ReportColumn;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.repository.ReportTemplateRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExportServiceTest {

    private ReportTemplateRepository templateRepository;
    private ObjectMapper objectMapper;
    private ExportService exportService;

    @BeforeEach
    void setUp() {
        templateRepository = Mockito.mock(ReportTemplateRepository.class);
        objectMapper = new ObjectMapper();
        exportService = new ExportService(objectMapper, templateRepository);
    }

    @Test
    void exportToExcel_generatesWorkbookUsingTemplate() throws Exception {
        ReportTemplate template = customTemplate();
        when(templateRepository.findTopByDefinitionIdOrderById(1L)).thenReturn(Optional.of(template));

        RunDetailDto detail = detailedRun(1L);
        byte[] workbookBytes = exportService.exportToExcel(detail);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);

            Sheet summary = workbook.getSheet("Summary");
            assertThat(summary).isNotNull();
            assertThat(findCellValue(summary, "Template")).isEqualTo("Custom Layout");

            Sheet matched = workbook.getSheet("Matched");
            assertThat(matched).isNotNull();
            assertThat(matched.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(detail.summary().matched());
            assertThat(matched.getRow(3).getCell(1).getNumericCellValue()).isGreaterThan(0.0);

            Sheet mismatched = workbook.getSheet("Mismatched");
            assertThat(mismatched).isNotNull();
            Row mismatchRow = mismatched.getRow(1);
            assertThat(mismatchRow).isNotNull();
            assertThat(mismatchRow.getCell(0).getNumericCellValue()).isEqualTo(1d);

            int commentsIdx = columnIndex(mismatched, "Comments");
            assertThat(commentsIdx).isGreaterThan(-1);
            assertThat(mismatchRow.getCell(commentsIdx).getStringCellValue())
                    .contains("uid=ops1: Needs review");

            int amountAIdx = columnIndex(mismatched, "Amount A");
            Cell amountACell = mismatchRow.getCell(amountAIdx);
            assertThat(amountACell.getNumericCellValue()).isEqualTo(100.00d);
            assertThat(amountACell.getCellStyle().getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(amountACell.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_YELLOW.getIndex());

            int amountBIdx = columnIndex(mismatched, "Amount B");
            assertThat(mismatchRow.getCell(amountBIdx).getNumericCellValue()).isEqualTo(95.00d);

            int riskIdx = columnIndex(mismatched, "Risk Score A");
            Cell riskScoreCell = mismatchRow.getCell(riskIdx);
            if (riskScoreCell.getCellType() == CellType.NUMERIC) {
                assertThat(Double.isNaN(riskScoreCell.getNumericCellValue())).isTrue();
            } else {
                assertThat(riskScoreCell.getCellType()).isEqualTo(CellType.ERROR);
            }

            int statusIdx = columnIndex(mismatched, "Status");
            assertThat(mismatchRow.getCell(statusIdx).getStringCellValue()).isEqualTo("OPEN");

            int unknownIdx = columnIndex(mismatched, "Unknown");
            assertThat(stringValue(mismatchRow.getCell(unknownIdx))).isEmpty();

            Sheet missing = workbook.getSheet("Missing");
            assertThat(missing).isNotNull();
            Row missingRow = missing.getRow(1);
            assertThat(missingRow).isNotNull();
            assertThat(missingRow.getCell(0).getNumericCellValue()).isEqualTo(2d);
            assertThat(missingRow.getCell(columnIndex(missing, "Amount A")).getNumericCellValue())
                    .isEqualTo(50.0d);
            assertThat(stringValue(missingRow.getCell(columnIndex(missing, "Amount B")))).isEmpty();
        }

        verify(templateRepository).findTopByDefinitionIdOrderById(1L);
    }

    @Test
    void exportToExcel_fallsBackToDefaultTemplateWhenNoneConfigured() throws Exception {
        when(templateRepository.findTopByDefinitionIdOrderById(99L)).thenReturn(Optional.empty());

        RunDetailDto detail = detailedRun(99L);
        byte[] workbookBytes = exportService.exportToExcel(detail);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet summary = workbook.getSheet("Summary");
            assertThat(summary).isNotNull();
            assertThat(findCellValue(summary, "Template")).isEqualTo("Default Layout");

            Sheet mismatched = workbook.getSheet("Mismatched");
            assertThat(mismatched).isNotNull();
            assertThat(columnIndex(mismatched, "Source A")).isGreaterThan(-1);
            assertThat(columnIndex(mismatched, "Source B")).isGreaterThan(-1);

            Sheet missing = workbook.getSheet("Missing");
            assertThat(missing).isNotNull();
            Row missingRow = missing.getRow(1);
            assertThat(missingRow).isNotNull();
            assertThat(missingRow.getCell(0).getNumericCellValue()).isEqualTo(2d);
        }

        verify(templateRepository).findTopByDefinitionIdOrderById(99L);
    }

    private String findCellValue(Sheet sheet, String label) {
        for (Row row : sheet) {
            Cell cell = row.getCell(0);
            if (cell != null && label.equals(cell.getStringCellValue())) {
                Cell valueCell = row.getCell(1);
                return valueCell != null ? valueCell.getStringCellValue() : "";
            }
        }
        return "";
    }

    private int columnIndex(Sheet sheet, String header) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return -1;
        }
        for (Cell cell : headerRow) {
            if (header.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private String stringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> Double.toString(cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case BLANK -> "";
            default -> "";
        };
    }

    private ReportTemplate customTemplate() {
        ReportTemplate template = new ReportTemplate();
        template.setName("Custom Layout");
        template.setDescription("Coverage template");
        template.setIncludeMatched(true);
        template.setIncludeMismatched(true);
        template.setIncludeMissing(true);
        template.setHighlightDifferences(true);

        template.getColumns().add(column(template, "Amount A", ReportColumnSource.SOURCE_A, "amount", 1, true));
        template.getColumns().add(column(template, "Amount B", ReportColumnSource.SOURCE_B, "amount", 2, true));
        template.getColumns().add(column(template, "Risk Score A", ReportColumnSource.SOURCE_A, "riskScore", 3, true));
        template.getColumns().add(column(template, "Status", ReportColumnSource.BREAK_METADATA, "status", 4, false));
        template.getColumns().add(column(template, "Comments", ReportColumnSource.BREAK_METADATA, "comments", 5, false));
        template.getColumns().add(column(template, "Unknown", ReportColumnSource.BREAK_METADATA, "unexpected", 6, false));
        return template;
    }

    private ReportColumn column(
            ReportTemplate template,
            String header,
            ReportColumnSource source,
            String field,
            int order,
            boolean highlight) {
        ReportColumn column = new ReportColumn();
        column.setTemplate(template);
        column.setHeader(header);
        column.setSource(source);
        column.setSourceField(field);
        column.setDisplayOrder(order);
        column.setHighlightDifferences(highlight);
        return column;
    }

    private RunDetailDto detailedRun(long definitionId) {
        ReconciliationSummaryDto summary = new ReconciliationSummaryDto(
                definitionId,
                2001L,
                Instant.parse("2024-03-18T09:00:00Z"),
                TriggerType.MANUAL_API,
                "ops-user",
                "batch-42",
                null,
                7,
                2,
                1);

        RunAnalyticsDto analytics = new RunAnalyticsDto(
                Map.of("OPEN", 2L, "CLOSED", 1L),
                Map.of("MISMATCH", 1L, "SOURCE_MISSING", 1L),
                Map.of(
                        "Payments", 5L,
                        "FX", 3L,
                        "Loans", 2L,
                        "Securities", 1L,
                        "Cards", 4L,
                        "Derivatives", 6L),
                Map.of("US", 3L, "EU", 1L),
                Map.of("0-1d", 2L, "1-3d", 1L),
                2,
                3,
                7);

        List<BreakItemDto> breaks = List.of(mismatchedBreak(), missingBreak());
        FilterMetadataDto filters = new FilterMetadataDto(
                List.of("Payments"),
                List.of("Wire"),
                List.of("US", "EU"),
                List.of(BreakStatus.OPEN));

        return new RunDetailDto(summary, analytics, breaks, filters);
    }

    private BreakItemDto mismatchedBreak() {
        return new BreakItemDto(
                1L,
                BreakType.MISMATCH,
                BreakStatus.OPEN,
                Map.of("product", "Payments", "subProduct", "Wire", "entity", "US"),
                List.of(BreakStatus.CLOSED),
                Instant.parse("2024-03-18T09:15:00Z"),
                Map.of(
                        "CASH",
                        Map.of("amount", new BigDecimal("100.00"), "tradeId", "TR-1", "riskScore", Double.NaN),
                        "GL",
                        Map.of("amount", new BigDecimal("95.00"), "tradeId", "TR-1", "riskScore", Double.NaN)),
                List.of(),
                List.of(new BreakCommentDto(10L, "uid=ops1", "NOTE", "Needs review", Instant.parse("2024-03-18T10:00:00Z"))));
    }

    private BreakItemDto missingBreak() {
        return new BreakItemDto(
                2L,
                BreakType.SOURCE_MISSING,
                BreakStatus.OPEN,
                Map.of("product", "Payments", "subProduct", "Wire", "entity", "EU"),
                List.of(),
                Instant.parse("2024-03-18T08:30:00Z"),
                Map.of("CASH", Map.of("amount", new BigDecimal("50.00"), "tradeId", "TR-2")),
                List.of("GL"),
                List.of());
    }
}
