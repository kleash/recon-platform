package com.universal.reconciliation.service.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.ExportFormat;
import com.universal.reconciliation.domain.enums.TriggerType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatasetExportWriterTest {

    private DatasetExportWriter writer;

    private DatasetRow sampleRow;

    @BeforeEach
    void setUp() {
        writer = new DatasetExportWriter(new ObjectMapper());
        sampleRow = new DatasetRow(
                100L,
                200L,
                Instant.parse("2024-05-01T00:15:30Z"),
                TriggerType.MANUAL_API,
                BreakStatus.OPEN,
                "MISMATCH",
                Instant.parse("2024-05-01T00:16:00Z"),
                Map.of("product_code", "FX-SPOT"),
                "maker1",
                "checker1",
                "Needs review",
                List.of("SOURCE_B"),
                "maker1",
                Instant.parse("2024-05-01T00:30:00Z"));
    }

    @Test
    void writeCsvShouldIncludeHeadersAndValues() {
        byte[] bytes = writer.write(
                ExportFormat.CSV,
                List.of(sampleRow),
                List.of("product_code"),
                Map.of("filterSummary", "{\"status\":\"OPEN\"}"));

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("Break ID,Run ID,Run Time (SGT)");
        assertThat(csv).contains("Product code");
        assertThat(csv).contains("FX-SPOT");
        assertThat(csv).contains("Needs review");
    }

    @Test
    void writeJsonlShouldSerialiseRows() {
        byte[] bytes = writer.write(
                ExportFormat.JSONL,
                List.of(sampleRow),
                List.of("product_code"),
                Map.of("filterSummary", "{\"status\":\"OPEN\"}"));

        String jsonl = new String(bytes, StandardCharsets.UTF_8).trim();
        assertThat(jsonl).contains("\"breakId\":100");
        assertThat(jsonl).contains("\"attributes\":{" + "\"product_code\":\"FX-SPOT\"" + "}");
    }

    @Test
    void writeXlsxShouldCreateWorkbook() throws Exception {
        byte[] bytes = writer.write(
                ExportFormat.XLSX,
                List.of(sampleRow),
                List.of("product_code"),
                Map.of("filterSummary", "{\"status\":\"OPEN\"}"));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheet("Summary").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Generated At");
            assertThat(workbook.getSheet("Dataset").getRow(1).getCell(0).getNumericCellValue())
                    .isEqualTo(100d);
        }
    }

    @Test
    void normaliseAttributeKeysShouldSort() {
        assertThat(DatasetExportWriter.normaliseAttributeKeys(Set.of("b", "a", "c")))
                .containsExactly("a", "b", "c");
    }

    @Test
    void writePdfShouldBeUnsupported() {
        assertThatThrownBy(() -> writer.write(ExportFormat.PDF, List.of(), List.of(), Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
