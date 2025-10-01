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

    @Test
    void parsesCsvWithHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.csv",
                "text/csv",
                "Amount,Fee\n100,10\n200,20".getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.CSV,
                true,
                ",",
                null,
                null,
                null,
                null,
                null);

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
        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.DELIMITED,
                false,
                "|",
                null,
                null,
                null,
                null,
                null);

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows.get(0)).containsEntry("COLUMN_1", "100");
        assertThat(rows.get(0)).containsEntry("COLUMN_2", "OK");
    }

    @Test
    void parsesJsonUsingRecordPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.json",
                "application/json",
                "{\"data\":{\"items\":[{\"amount\":100},{\"amount\":200}]}}".getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.JSON,
                false,
                null,
                null,
                "data.items",
                null,
                null,
                null);

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
        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.JSON,
                false,
                null,
                null,
                "data.items[1]",
                null,
                null,
                null);

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

        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.JSON,
                false,
                null,
                null,
                null,
                null,
                null,
                null);

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
            SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                    TransformationSampleFileType.EXCEL,
                    true,
                    null,
                    "SheetA",
                    null,
                    null,
                    null,
                    null);

            List<Map<String, Object>> rows = service.parseSamples(request, file);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).containsEntry("Amount", 123.45);
            assertThat(rows.get(0)).containsEntry("Currency", "USD");
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
        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.XML,
                false,
                null,
                null,
                "data.items.item",
                null,
                null,
                null);

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
        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.JSON,
                false,
                null,
                null,
                "items[0].payload",
                null,
                null,
                null);

        assertThatThrownBy(() -> service.parseSamples(request, file))
                .isInstanceOf(TransformationEvaluationException.class)
                .hasMessageContaining("record path");
    }
}
