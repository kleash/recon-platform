package com.universal.reconciliation.ingestion.sdk.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcCsvBatchBuilderTest {

    @Test
    void rendersCsvFromQuery() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");

        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.execute("CREATE TABLE cash (id VARCHAR(20), amount DECIMAL(10,2))");
        template.update("INSERT INTO cash (id, amount) VALUES (?, ?)", "T-1", 100.25);
        template.update("INSERT INTO cash (id, amount) VALUES (?, ?)", "T-2", 50.75);

        JdbcCsvBatchBuilder builder = new JdbcCsvBatchBuilder(template);
        IngestionBatch batch = builder.build(
                "CASH",
                "cash-ledger",
                "SELECT id AS transactionId, amount FROM cash ORDER BY id",
                Map.of(),
                List.of("transactionId", "amount"),
                Map.of());

        String csv = new String(batch.getPayload(), StandardCharsets.UTF_8);
        assertThat(csv).contains("transactionId,amount");
        assertThat(csv).contains("T-1,100.25");
        assertThat(csv).contains("T-2,50.75");
    }
}
