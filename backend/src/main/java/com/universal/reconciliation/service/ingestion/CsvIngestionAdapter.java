package com.universal.reconciliation.service.ingestion;

import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Simple CSV ingestion adapter used for the bootstrapped examples. The
 * implementation relies on Apache Commons CSV so quoted values and
 * embedded delimiters are handled correctly while keeping the
 * orchestration layer agnostic of the underlying parser.
 */
@Component
public class CsvIngestionAdapter implements IngestionAdapter {

    private static final String OPTION_DELIMITER = "delimiter";
    private static final String OPTION_CHARSET = "charset";

    @Override
    public IngestionAdapterType getType() {
        return IngestionAdapterType.CSV_FILE;
    }

    @Override
    public List<Map<String, Object>> readRecords(IngestionAdapterRequest request) {
        char delimiter = resolveDelimiter(request.options());
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setDelimiter(delimiter)
                .build();
        try (InputStream inputStream = request.inputStreamSupplier().get();
                Reader reader = new InputStreamReader(inputStream, resolveCharset(request.options()));
                CSVParser parser = new CSVParser(reader, csvFormat)) {
            List<String> headers = parser.getHeaderNames();
            if (headers == null || headers.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String header : headers) {
                    if (!record.isMapped(header) || !record.isSet(header)) {
                        continue;
                    }
                    row.put(header, record.get(header));
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV input", e);
        }
    }

    private Charset resolveCharset(Map<String, Object> options) {
        if (options == null) {
            return StandardCharsets.UTF_8;
        }
        Object value = options.get(OPTION_CHARSET);
        if (value instanceof Charset charset) {
            return charset;
        }
        if (value instanceof String charsetName && StringUtils.hasText(charsetName)) {
            return Charset.forName(charsetName);
        }
        return StandardCharsets.UTF_8;
    }

    private char resolveDelimiter(Map<String, Object> options) {
        if (options == null) {
            return ',';
        }
        Object value = options.get(OPTION_DELIMITER);
        if (value instanceof Character character) {
            return character;
        }
        if (value instanceof String delimiter && StringUtils.hasLength(delimiter)) {
            return delimiter.charAt(0);
        }
        return ',';
    }
}
