package com.universal.reconciliation.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelIngestionAdapterTest {

    private final ExcelIngestionAdapter adapter = new ExcelIngestionAdapter();

    @Test
    void readsAllSheetsAndDecoratesWithSheetColumn() throws IOException {
        byte[] payload = buildWorkbook(workbook -> {
            Sheet positions = workbook.createSheet("Positions");
            Row positionsHeader = positions.createRow(0);
            positionsHeader.createCell(0).setCellValue("gm_trade_id");
            positionsHeader.createCell(1).setCellValue("gm_amount_local");
            Row first = positions.createRow(1);
            first.createCell(0).setCellValue("T-100");
            first.createCell(1).setCellValue(1250.50);

            Sheet financing = workbook.createSheet("Financing");
            Row financingHeader = financing.createRow(0);
            financingHeader.createCell(0).setCellValue("gm_trade_id");
            financingHeader.createCell(1).setCellValue("gm_amount_local");
            Row second = financing.createRow(1);
            second.createCell(0).setCellValue("T-101");
            second.createCell(1).setCellValue(830.15);
        });

        List<Map<String, Object>> records = adapter.readRecords(new IngestionAdapterRequest(
                () -> new ByteArrayInputStream(payload),
                Map.of(
                        ExcelIngestionAdapter.OPTION_HAS_HEADER, true,
                        ExcelIngestionAdapter.OPTION_INCLUDE_ALL_SHEETS, true,
                        ExcelIngestionAdapter.OPTION_INCLUDE_SHEET_COLUMN, true,
                        ExcelIngestionAdapter.OPTION_SHEET_COLUMN_NAME, "sheet_name")));

        assertThat(records)
                .hasSize(2)
                .extracting(row -> row.get("sheet_name"))
                .containsExactlyInAnyOrder("Positions", "Financing");
        assertThat(records)
                .extracting(row -> row.get("gm_trade_id"))
                .containsExactlyInAnyOrder("T-100", "T-101");
    }

    @Test
    void restrictsToRequestedSheetsAndSupportsSkipRows() throws IOException {
        byte[] payload = buildWorkbook(workbook -> {
            Sheet positions = workbook.createSheet("Positions");
            Row positionsHeader = positions.createRow(0);
            positionsHeader.createCell(0).setCellValue("gm_trade_id");
            positionsHeader.createCell(1).setCellValue("gm_amount_local");
            Row skipRow = positions.createRow(1);
            skipRow.createCell(0).setCellValue("IGNORED");
            skipRow.createCell(1).setCellValue(0);
            Row retained = positions.createRow(2);
            retained.createCell(0).setCellValue("T-102");
            retained.createCell(1).setCellValue(910.42);

            Sheet alternatives = workbook.createSheet("Alternatives");
            Row altHeader = alternatives.createRow(0);
            altHeader.createCell(0).setCellValue("gm_trade_id");
            altHeader.createCell(1).setCellValue("gm_amount_local");
            Row altRow = alternatives.createRow(1);
            altRow.createCell(0).setCellValue("T-999");
            altRow.createCell(1).setCellValue(12.34);
        });

        List<Map<String, Object>> records = adapter.readRecords(new IngestionAdapterRequest(
                () -> new ByteArrayInputStream(payload),
                Map.of(
                        ExcelIngestionAdapter.OPTION_HAS_HEADER, true,
                        ExcelIngestionAdapter.OPTION_SHEET_NAMES, List.of("Positions"),
                        ExcelIngestionAdapter.OPTION_SKIP_ROWS, 1)));

        assertThat(records)
                .hasSize(1)
                .first()
                .satisfies(row -> {
                    assertThat(row.get("gm_trade_id")).isEqualTo("T-102");
                    assertThat(row).doesNotContainKey("gm_amount_local_Alternatives");
                });
    }

    private byte[] buildWorkbook(WorkbookBuilder builder) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            builder.accept(workbook);
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }

    @FunctionalInterface
    private interface WorkbookBuilder {
        void accept(XSSFWorkbook workbook) throws IOException;
    }
}
