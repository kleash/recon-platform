package com.universal.reconciliation.service.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationPreviewUploadRequest;
import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class TransformationSampleFileServiceTest {

    private final TransformationSampleFileService service =
            new TransformationSampleFileService(new ObjectMapper(), 2L * 1024 * 1024);
    private static final List<String> NO_SHEETS = List.of();

    private SourceTransformationPreviewUploadRequest request(
            TransformationSampleFileType type,
            boolean hasHeader,
            String delimiter,
            String sheetName,
            List<String> sheetNames,
            boolean includeAllSheets,
            boolean includeSheetNameColumn,
            String sheetNameColumn,
            String recordPath,
            String encoding,
            Integer limit,
            Integer skipRows) {
        return new SourceTransformationPreviewUploadRequest(
                type,
                hasHeader,
                delimiter,
                sheetName,
                sheetNames,
                includeAllSheets,
                includeSheetNameColumn,
                sheetNameColumn,
                recordPath,
                encoding,
                limit,
                skipRows,
                null);
    }

    @Test
    void parsesCsvWithHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.csv",
                "text/csv",
                "Amount,Fee\n100,10\n200,20".getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request =
                request(TransformationSampleFileType.CSV, true, ",", null, NO_SHEETS, false, false, null, null, null, null, null);

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("Amount", "100");
    }

    @Test
    void assignsSyntheticHeadersWhenMissing() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.csv",
                "text/csv",
                "100|OK\n200|FAIL".getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request =
                request(TransformationSampleFileType.DELIMITED, false, "|", null, NO_SHEETS, false, false, null, null, null, null, null);

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows.get(0)).containsEntry("COLUMN_1", "100");
        assertThat(rows.get(0)).containsEntry("COLUMN_2", "OK");
    }

    @Test
    void skipRowsAppliesToDelimitedFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "skip.csv",
                "text/csv",
                "colA,colB\n1,one\n2,two\n3,three".getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request =
                request(TransformationSampleFileType.CSV, true, ",", null, NO_SHEETS, false, false, null, null, null, null, 1);

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows)
                .hasSize(2)
                .first()
                .satisfies(row -> assertThat(row.get("colA")).isEqualTo("2"));
    }

    @Test
    void parsesJsonUsingRecordPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.json",
                "application/json",
                "{\"data\":{\"items\":[{\"amount\":100},{\"amount\":200}]}}".getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request =
                request(TransformationSampleFileType.JSON, false, null, null, NO_SHEETS, false, false, null, "data.items", null, null, null);

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsEntry("amount", 200);
    }

    @Test
    void parsesJsonArrayElementByIndex() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.json",
                "application/json",
                "{\"data\":{\"items\":[{\"amount\":100},{\"amount\":200}]}}".getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request =
                request(TransformationSampleFileType.JSON, false, null, null, NO_SHEETS, false, false, null, "data.items[1]", null, null, null);

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("amount", 200);
    }

    @Test
    void enforcesUploadSizeLimit() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(11L);

        TransformationSampleFileService smallLimitService =
                new TransformationSampleFileService(new ObjectMapper(), 10L);

        SourceTransformationPreviewUploadRequest request =
                request(TransformationSampleFileType.JSON, false, null, null, NO_SHEETS, false, false, null, null, null, null, null);

        assertThatThrownBy(() -> smallLimitService.parseSamples(request, file))
                .isInstanceOf(TransformationEvaluationException.class)
                .hasMessageContaining("upload limit");
    }

    @Test
    void parsesExcelFileWithHeader() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("SheetA");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Amount");
            header.createCell(1).setCellValue("Currency");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(123.45);
            row.createCell(1).setCellValue("USD");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "sample.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
            SourceTransformationPreviewUploadRequest request =
                    request(TransformationSampleFileType.EXCEL, true, null, "SheetA", NO_SHEETS, false, false, null, null, null, null, null);

            List<Map<String, Object>> rows = service.parseSamples(request, file);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).containsEntry("Amount", 123.45);
            assertThat(rows.get(0)).containsEntry("Currency", "USD");
        }
    }

    @Test
    void parsesMultipleExcelSheetsAndAppendsSheetName() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet jan = workbook.createSheet("Jan");
            Row janHeader = jan.createRow(0);
            janHeader.createCell(0).setCellValue("Id");
            janHeader.createCell(1).setCellValue("Amount");
            Row janRow = jan.createRow(1);
            janRow.createCell(0).setCellValue("J-1");
            janRow.createCell(1).setCellValue(10);

            Sheet feb = workbook.createSheet("Feb");
            Row febHeader = feb.createRow(0);
            febHeader.createCell(0).setCellValue("Id");
            febHeader.createCell(1).setCellValue("Amount");
            Row febRow = feb.createRow(1);
            febRow.createCell(0).setCellValue("F-1");
            febRow.createCell(1).setCellValue(20);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "multi.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());

            SourceTransformationPreviewUploadRequest request =
                    request(TransformationSampleFileType.EXCEL, true, null, null, List.of("Jan", "Feb"), false, true, "sheetName", null, null, null, null);

            List<Map<String, Object>> rows = service.parseSamples(request, file);

            assertThat(rows)
                    .hasSize(2)
                    .anySatisfy(row -> {
                        if ("J-1".equals(row.get("Id"))) {
                            assertThat(row.get("sheetName")).isEqualTo("Jan");
                        }
                    })
                    .anySatisfy(row -> {
                        if ("F-1".equals(row.get("Id"))) {
                            assertThat(row.get("sheetName")).isEqualTo("Feb");
                        }
                    });
        }
    }

    @Test
    void parsesXmlUsingNestedRecordPath() {
        String xml = """
                <root>
                  <data>
                    <items>
                      <item><amount>7</amount></item>
                      <item><amount>9</amount></item>
                    </items>
                  </data>
                </root>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.xml",
                "application/xml",
                xml.getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request =
                request(TransformationSampleFileType.XML, false, null, null, NO_SHEETS, false, false, null, "data.items.item", null, null, null);

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("amount", "7");
        assertThat(rows.get(1)).containsEntry("amount", "9");
    }

    @Test
    void throwsWhenRecordPathMissingData() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.json",
                "application/json",
                "{\"items\":[]}".getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request =
                request(TransformationSampleFileType.JSON, false, null, null, NO_SHEETS, false, false, null, "items[0].payload", null, null, null);

        assertThatThrownBy(() -> service.parseSamples(request, file))
                .isInstanceOf(TransformationEvaluationException.class)
                .hasMessageContaining("record path");
    }

    @Test
    void listSheetNamesReturnsWorkbookSheets() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Alpha");
            workbook.createSheet("Beta");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "sheets.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());

            assertThat(service.listSheetNames(file)).containsExactly("Alpha", "Beta");
        }
    }
}
