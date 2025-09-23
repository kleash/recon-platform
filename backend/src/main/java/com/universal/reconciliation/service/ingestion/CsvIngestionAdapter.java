package com.universal.reconciliation.service.ingestion;

import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Simple CSV ingestion adapter used for the bootstrapped examples. The
 * adapter keeps parsing logic lightweight so alternative implementations
 * (e.g. leveraging Apache Commons CSV) can be swapped in later without
 * touching the ingestion orchestration layer.
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
        try (InputStream inputStream = request.inputStreamSupplier().get();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        inputStream, resolveCharset(request.options())))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                return List.of();
            }
            String[] headers = headerLine.split(resolveDelimiter(request.options()));
            List<Map<String, Object>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                String[] values = line.split(resolveDelimiter(request.options()), -1);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i], values[i].trim());
                }
                rows.add(row);
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

    private String resolveDelimiter(Map<String, Object> options) {
        if (options == null) {
            return ",";
        }
        Object value = options.get(OPTION_DELIMITER);
        if (value instanceof Character character) {
            return character.toString();
        }
        if (value instanceof String delimiter && StringUtils.hasLength(delimiter)) {
            return delimiter;
        }
        return ",";
    }
}
