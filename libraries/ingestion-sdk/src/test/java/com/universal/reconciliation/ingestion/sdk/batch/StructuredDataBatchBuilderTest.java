package com.universal.reconciliation.ingestion.sdk.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class StructuredDataBatchBuilderTest {

    private final StructuredDataBatchBuilder builder = new StructuredDataBatchBuilder();

    @Test
    void convertsJsonArrayToCsv() throws IOException {
        String json = "[{\"id\":1,\"amount\":100.0},{\"id\":2,\"amount\":50.5}]";
        IngestionBatch batch = builder.fromJsonArray(
                "JSON",
                "json-array",
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                List.of("id", "amount"),
                Map.of("ingestionMode", "append"));

        assertThat(readCsv(batch))
                .contains("id,amount")
                .contains("1,100.0")
                .contains("2,50.5");
        assertThat(batch.getOptions()).containsEntry("ingestionMode", "append");
    }

    @Test
    void convertsDelimitedTextToCsv() throws IOException {
        String content = "id|amount|currency\n1|25.00|USD\n2|13.37|EUR";
        IngestionBatch batch = builder.fromDelimitedText(
                "PIPE",
                "pipe-text",
                new StringReader(content),
                '|',
                true,
                List.of(),
                Map.of());

        String csv = readCsv(batch);
        assertThat(csv).contains("id,amount,currency");
        assertThat(csv).contains("1,25.00,USD");
        assertThat(csv).contains("2,13.37,EUR");
    }

    @Test
    void convertsExcelWorksheetToCsv() throws IOException {
        ByteArrayOutputStream workbookStream = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Positions");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("positionId");
            header.createCell(1).setCellValue("quantity");
            header.createCell(2).setCellValue("symbol");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("POS-1");
            row1.createCell(1).setCellValue(250);
            row1.createCell(2).setCellValue("AAPL");
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("POS-2");
            row2.createCell(1).setCellValue(125.5);
            row2.createCell(2).setCellValue("MSFT");
            workbook.write(workbookStream);
        }

        IngestionBatch batch = builder.fromExcel(
                "EXCEL",
                "positions",
                new ByteArrayInputStream(workbookStream.toByteArray()),
                "Positions",
                true,
                List.of(),
                Map.of("sheet", "Positions"));

        String csv = readCsv(batch);
        assertThat(csv).contains("positionId,quantity,symbol");
        assertThat(csv).contains("POS-1,250,AAPL");
        assertThat(csv).contains("POS-2,125.5,MSFT");
    }

    @Test
    void buildsFromIterableRecords() throws IOException {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", "R-1");
        first.put("amount", "15.00");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("id", "R-2");
        second.put("amount", "32.10");

        IngestionBatch batch = builder.fromRecords(
                "ITERABLE",
                "records",
                List.of(first, second),
                List.of("id", "amount"),
                Map.of());

        String csv = readCsv(batch);
        assertThat(csv).contains("id,amount");
        assertThat(csv).contains("R-1,15.00");
        assertThat(csv).contains("R-2,32.10");
    }

    private String readCsv(IngestionBatch batch) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        batch.writePayload(buffer);
        String csv = buffer.toString(StandardCharsets.UTF_8);
        batch.discardPayload();
        try (CSVParser ignored = CSVFormat.DEFAULT.builder().setSkipHeaderRecord(false).build().parse(new StringReader(csv))) {
            // ensure CSV is parsable
        }
        return csv;
    }
}

