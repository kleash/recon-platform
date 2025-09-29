package com.universal.reconciliation.examples.ingestionsdk;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import com.universal.reconciliation.ingestion.sdk.batch.StructuredDataBatchBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("format-samples")
class MultiFormatIngestionRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiFormatIngestionRunner.class);

    private final StructuredDataBatchBuilder structuredDataBatchBuilder = new StructuredDataBatchBuilder();

    @Override
    public void run(String... args) throws Exception {
        LOGGER.info("Building ingestion batches from JSON arrays, Excel, and pipe-delimited text");

        previewJsonArray();
        previewExcelWorksheet();
        previewPipeDelimitedText();

        LOGGER.info("Multi-format ingestion samples complete");
    }

    private void previewJsonArray() throws IOException {
        IngestionBatch batch = structuredDataBatchBuilder.fromJsonArray(
                "CASH_JSON",
                "cash-ledger-json",
                jsonArrayStream(),
                List.of("transactionId", "amount", "currency", "tradeDate", "product", "subProduct", "entityName"),
                Map.of("format", "json-array"));
        try {
            LOGGER.info("JSON array batch preview:{}{}", System.lineSeparator(), renderCsv(batch));
        } finally {
            batch.discardPayload();
        }
    }

    private void previewExcelWorksheet() throws IOException {
        IngestionBatch batch = structuredDataBatchBuilder.fromExcel(
                "CUSTODIAN_XLSX",
                "custodian-positions",
                new ByteArrayInputStream(buildCustodianWorkbook()),
                "Positions",
                true,
                List.of(),
                Map.of("format", "excel"));
        try {
            LOGGER.info("Excel worksheet batch preview:{}{}", System.lineSeparator(), renderCsv(batch));
        } finally {
            batch.discardPayload();
        }
    }

    private void previewPipeDelimitedText() throws IOException {
        IngestionBatch batch = structuredDataBatchBuilder.fromDelimitedText(
                "TRADES_PIPE",
                "trades-pipe",
                new StringReader(pipeDelimitedContent()),
                '|',
                true,
                List.of(),
                Map.of("format", "pipe-delimited"));
        try {
            LOGGER.info("Pipe-delimited batch preview:{}{}", System.lineSeparator(), renderCsv(batch));
        } finally {
            batch.discardPayload();
        }
    }

    private String renderCsv(IngestionBatch batch) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        batch.writePayload(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private byte[] buildCustodianWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Positions");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("positionId");
            header.createCell(1).setCellValue("quantity");
            header.createCell(2).setCellValue("symbol");

            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("POS-1");
            first.createCell(1).setCellValue(250);
            first.createCell(2).setCellValue("AAPL");

            Row second = sheet.createRow(2);
            second.createCell(0).setCellValue("POS-2");
            second.createCell(1).setCellValue(125.5);
            second.createCell(2).setCellValue("MSFT");

            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }

    private ByteArrayInputStream jsonArrayStream() {
        String payload = """
                [
                  {"transactionId":"TXN-1001","amount":1550.45,"currency":"USD","tradeDate":"2024-05-15",
                   "product":"Swap","subProduct":"Interest Rate","entityName":"Universal Bank"},
                  {"transactionId":"TXN-1002","amount":-320.10,"currency":"EUR","tradeDate":"2024-05-16",
                   "product":"Forward","subProduct":"FX","entityName":"Global Markets"}
                ]
                """;
        return new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String pipeDelimitedContent() {
        return """
                tradeId|tradeDate|symbol|quantity|price
                TR-1001|2024-05-15|AAPL|150|185.42
                TR-1002|2024-05-15|MSFT|250|312.18
                TR-1003|2024-05-16|GOOGL|75|132.55
                """;
    }
}

