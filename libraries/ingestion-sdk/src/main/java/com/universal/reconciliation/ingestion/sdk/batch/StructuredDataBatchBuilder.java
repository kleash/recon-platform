package com.universal.reconciliation.ingestion.sdk.batch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.util.StringUtils;

/**
 * Builder for converting structured file formats (JSON arrays, spreadsheets, delimited text) into CSV batches.
 */
public final class StructuredDataBatchBuilder {

    private final ObjectMapper objectMapper;
    private final DataFormatter dataFormatter;

    public StructuredDataBatchBuilder() {
        this(new ObjectMapper());
    }

    public StructuredDataBatchBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.dataFormatter = new DataFormatter();
    }

    public IngestionBatch fromRecords(
            String sourceCode,
            String label,
            Iterable<Map<String, Object>> records,
            List<String> columns,
            Map<String, Object> options)
            throws IOException {
        return writeBatch(sourceCode, label, options, writer -> CsvRenderer.streamRows(records, columns, writer));
    }

    public IngestionBatch fromJsonArray(
            String sourceCode,
            String label,
            InputStream jsonStream,
            List<String> columns,
            Map<String, Object> options)
            throws IOException {
        Objects.requireNonNull(jsonStream, "jsonStream");
        Path tempFile = Files.createTempFile("ingestion-json-", ".csv");
        tempFile.toFile().deleteOnExit();
        try (InputStream in = jsonStream;
                JsonParser parser = objectMapper.getFactory().createParser(in);
                BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken == null) {
                CsvRenderer.createPrinter(writer, columns == null ? List.of() : new ArrayList<>(columns)).flush();
            } else {
                if (firstToken != JsonToken.START_ARRAY) {
                    throw new IOException("Expected JSON array but received: " + firstToken);
                }
                JsonToken elementToken = parser.nextToken();
                if (elementToken == JsonToken.END_ARRAY || elementToken == null) {
                    CsvRenderer.createPrinter(writer, columns == null ? List.of() : new ArrayList<>(columns)).flush();
                } else {
                    ObjectReader reader = objectMapper.readerFor(Map.class);
                    try (MappingIterator<Map<String, Object>> iterator = reader.readValues(parser)) {
                        CsvRenderer.streamIterator(iterator, columns, writer);
                    }
                }
            }
            writer.flush();
        } catch (IOException | RuntimeException e) {
            deleteQuietly(tempFile, e);
            throw e;
        }
        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payloadFile(tempFile, true)
                .options(options == null ? Map.of() : options)
                .build();
    }

    public IngestionBatch fromDelimitedText(
            String sourceCode,
            String label,
            InputStream stream,
            char delimiter,
            boolean hasHeader,
            List<String> columns,
            Map<String, Object> options)
            throws IOException {
        Objects.requireNonNull(stream, "stream");
        try (Reader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return fromDelimitedText(sourceCode, label, reader, delimiter, hasHeader, columns, options);
        }
    }

    public IngestionBatch fromDelimitedText(
            String sourceCode,
            String label,
            Reader reader,
            char delimiter,
            boolean hasHeader,
            List<String> columns,
            Map<String, Object> options)
            throws IOException {
        Objects.requireNonNull(reader, "reader");
        Path tempFile = Files.createTempFile("ingestion-delimited-", ".csv");
        tempFile.toFile().deleteOnExit();
        CSVFormat.Builder formatBuilder = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setTrim(true)
                .setIgnoreEmptyLines(true);
        if (hasHeader) {
            formatBuilder.setHeader();
            formatBuilder.setSkipHeaderRecord(true);
        }
        CSVFormat format = formatBuilder.build();
        try (CSVParser parser = format.parse(reader);
                BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            List<String> headers = columns != null && !columns.isEmpty() ? new ArrayList<>(columns) : null;
            if (hasHeader) {
                List<String> parsedHeaders = parser.getHeaderNames();
                if ((headers == null || headers.isEmpty()) && !parsedHeaders.isEmpty()) {
                    headers = new ArrayList<>(parsedHeaders);
                }
            }
            Iterator<CSVRecord> iterator = parser.iterator();
            CSVRecord firstRecord = iterator.hasNext() ? iterator.next() : null;
            if (headers == null || headers.isEmpty()) {
                if (firstRecord != null) {
                    headers = defaultHeaders(firstRecord.size());
                } else {
                    headers = List.of();
                }
            }
            CSVPrinter printer = CsvRenderer.createPrinter(writer, headers);
            if (firstRecord != null) {
                CsvRenderer.printRow(printer, toRow(firstRecord, headers), headers);
            }
            while (iterator.hasNext()) {
                CSVRecord record = iterator.next();
                CsvRenderer.printRow(printer, toRow(record, headers), headers);
            }
            printer.flush();
            writer.flush();
        } catch (IOException | RuntimeException e) {
            deleteQuietly(tempFile, e);
            throw e;
        }
        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payloadFile(tempFile, true)
                .options(options == null ? Map.of() : options)
                .build();
    }

    public IngestionBatch fromExcel(
            String sourceCode,
            String label,
            InputStream workbookStream,
            String sheetName,
            boolean hasHeaderRow,
            List<String> columns,
            Map<String, Object> options)
            throws IOException {
        Objects.requireNonNull(workbookStream, "workbookStream");
        Path tempFile = Files.createTempFile("ingestion-excel-", ".csv");
        tempFile.toFile().deleteOnExit();
        try (InputStream in = workbookStream;
                Workbook workbook = WorkbookFactory.create(in);
                BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            Sheet sheet = resolveSheet(workbook, sheetName);
            if (sheet == null) {
                throw new IOException("Sheet '" + sheetName + "' was not found in workbook");
            }
            Iterator<Row> iterator = sheet.iterator();
            List<String> headers = columns != null && !columns.isEmpty() ? new ArrayList<>(columns) : null;
            CSVPrinter printer = headers != null && !headers.isEmpty() ? CsvRenderer.createPrinter(writer, headers) : null;
            while (iterator.hasNext()) {
                Row row = iterator.next();
                if (row == null) {
                    continue;
                }
                boolean skipCurrentRow = false;
                if (printer == null) {
                    if (headers == null || headers.isEmpty()) {
                        if (hasHeaderRow) {
                            headers = extractHeaders(row);
                            printer = CsvRenderer.createPrinter(writer, headers);
                            skipCurrentRow = true;
                        } else {
                            headers = defaultHeaders(row.getLastCellNum() > 0 ? row.getLastCellNum() : row.getPhysicalNumberOfCells());
                            printer = CsvRenderer.createPrinter(writer, headers);
                        }
                    } else {
                        printer = CsvRenderer.createPrinter(writer, headers);
                        if (hasHeaderRow) {
                            skipCurrentRow = true;
                        }
                    }
                }
                if (skipCurrentRow) {
                    continue;
                }
                Map<String, Object> rowMap = toRow(row, headers);
                if (rowMap.values().stream().allMatch(value -> value == null || String.valueOf(value).isBlank())) {
                    continue;
                }
                CsvRenderer.printRow(printer, rowMap, headers);
            }
            if (printer == null) {
                List<String> emptyHeaders = headers != null ? headers : List.of();
                printer = CsvRenderer.createPrinter(writer, emptyHeaders);
            }
            printer.flush();
            writer.flush();
        } catch (IOException | RuntimeException e) {
            deleteQuietly(tempFile, e);
            throw e;
        }
        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payloadFile(tempFile, true)
                .options(options == null ? Map.of() : options)
                .build();
    }

    private IngestionBatch writeBatch(
            String sourceCode,
            String label,
            Map<String, Object> options,
            WriterConsumer writerConsumer)
            throws IOException {
        Path tempFile = Files.createTempFile("ingestion-structured-", ".csv");
        tempFile.toFile().deleteOnExit();
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            writerConsumer.accept(writer);
            writer.flush();
        } catch (IOException | RuntimeException e) {
            deleteQuietly(tempFile, e);
            throw e;
        }
        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payloadFile(tempFile, true)
                .options(options == null ? Map.of() : options)
                .build();
    }

    private static List<String> defaultHeaders(int size) {
        if (size <= 0) {
            return List.of();
        }
        return IntStream.range(0, size)
                .mapToObj(index -> "column" + (index + 1))
                .toList();
    }

    private Map<String, Object> toRow(CSVRecord record, List<String> headers) {
        Map<String, Object> row = new LinkedHashMap<>(headers.size());
        for (int i = 0; i < headers.size(); i++) {
            String value = i < record.size() ? record.get(i) : "";
            row.put(headers.get(i), value);
        }
        return row;
    }

    private Map<String, Object> toRow(Row row, List<String> headers) {
        Map<String, Object> result = new LinkedHashMap<>(headers.size());
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = cell == null ? "" : dataFormatter.formatCellValue(cell);
            result.put(headers.get(i), value);
        }
        return result;
    }

    private List<String> extractHeaders(Row row) {
        int cellCount = Math.max(row.getLastCellNum(), row.getPhysicalNumberOfCells());
        if (cellCount <= 0) {
            return List.of();
        }
        List<String> headers = new ArrayList<>(cellCount);
        for (int i = 0; i < cellCount; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String header = cell == null ? null : dataFormatter.formatCellValue(cell);
            if (!StringUtils.hasText(header)) {
                header = "column" + (i + 1);
            }
            headers.add(header.trim());
        }
        return headers;
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName) {
        if (StringUtils.hasText(sheetName)) {
            return workbook.getSheet(sheetName);
        }
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    private void deleteQuietly(Path path, Exception rootCause) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException suppressed) {
            rootCause.addSuppressed(suppressed);
        }
    }

    @FunctionalInterface
    private interface WriterConsumer {
        void accept(Writer writer) throws IOException;
    }
}

