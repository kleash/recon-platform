package com.universal.reconciliation.ingestion.sdk.batch;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
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
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, params == null ? Map.of() : params, (rs, rowNum) -> {
            int columnCount = rs.getMetaData().getColumnCount();
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String column = rs.getMetaData().getColumnLabel(i);
                row.put(column, rs.getObject(i));
            }
            return CsvRenderer.normalizeKeys(row);
        });
        byte[] payload = CsvRenderer.render(rows, columns == null ? null : new ArrayList<>(columns));
        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payload(payload)
                .options(options == null ? Map.of() : options)
                .build();
    }
}
