package com.universal.reconciliation.ingestion.sdk.batch;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Converts the result of a SQL query into a CSV-backed {@link IngestionBatch}.
 */
public final class JdbcCsvBatchBuilder {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCsvBatchBuilder(JdbcTemplate jdbcTemplate) {
        this(new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate")));
    }

    public JdbcCsvBatchBuilder(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public IngestionBatch build(String sourceCode, String label, String sql, Map<String, Object> params, List<String> columns, Map<String, Object> options) {
        Map<String, Object> effectiveParams = params == null ? Map.of() : params;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            jdbcTemplate.query(sql, new MapSqlParameterSource(effectiveParams), rs -> {
                try {
                    CsvRenderer.streamRows(rs, columns == null ? null : new ArrayList<>(columns), writer);
                } catch (IOException ioException) {
                    throw new UncheckedIOException("Failed to render CSV payload", ioException);
                }
                return null;
            });
        } catch (UncheckedIOException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render CSV payload", e);
        }
        byte[] payload = output.toByteArray();
        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payload(payload)
                .options(options == null ? Map.of() : options)
                .build();
    }
}
