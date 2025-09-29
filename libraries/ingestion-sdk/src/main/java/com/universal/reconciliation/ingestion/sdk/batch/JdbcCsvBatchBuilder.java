package com.universal.reconciliation.ingestion.sdk.batch;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
        Path tempFile;
        try {
            tempFile = Files.createTempFile("ingestion-jdbc-", ".csv");
            tempFile.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temporary CSV payload", e);
        }

        List<String> selectedColumns = columns == null ? null : new ArrayList<>(columns);
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            jdbcTemplate.query(sql, new MapSqlParameterSource(effectiveParams), rs -> {
                try {
                    CsvRenderer.streamRows(rs, selectedColumns, writer);
                } catch (IOException ioException) {
                    throw new UncheckedIOException("Failed to render CSV payload", ioException);
                }
                return null;
            });
            writer.flush();
        } catch (UncheckedIOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new UncheckedIOException("Failed to render CSV payload", e);
        }

        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payloadFile(tempFile, true)
                .options(options == null ? Map.of() : options)
                .build();
    }
}
