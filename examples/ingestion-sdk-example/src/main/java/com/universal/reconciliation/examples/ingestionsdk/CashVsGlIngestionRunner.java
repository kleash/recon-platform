package com.universal.reconciliation.examples.ingestionsdk;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import com.universal.reconciliation.ingestion.sdk.IngestionPipeline;
import com.universal.reconciliation.ingestion.sdk.IngestionScenario;
import com.universal.reconciliation.ingestion.sdk.batch.JdbcCsvBatchBuilder;
import com.universal.reconciliation.ingestion.sdk.batch.RestApiCsvBatchBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

@Component
class CashVsGlIngestionRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CashVsGlIngestionRunner.class);
    private static final List<String> CANONICAL_COLUMNS = List.of(
            "transactionId",
            "amount",
            "currency",
            "tradeDate",
            "product",
            "subProduct",
            "entityName");

    private static final String CASH_LEDGER_QUERY =
            "SELECT transaction_id AS transactionId, amount, currency, trade_date AS tradeDate, "
                    + "product, sub_product AS subProduct, entity_name AS entityName FROM cash_ledger ORDER BY transaction_id";

    private final IngestionPipeline pipeline;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;

    CashVsGlIngestionRunner(IngestionPipeline pipeline, JdbcTemplate jdbcTemplate, RestTemplate restTemplate) {
        this.pipeline = pipeline;
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        LOGGER.info("Preparing batches for CASH_VS_GL_SIMPLE");
        IngestionBatch cashBatch = buildCashBatch();
        IngestionBatch glBatch = buildGeneralLedgerBatch();

        IngestionScenario scenario = IngestionScenario.builder("cash-vs-gl-sample")
                .reconciliationCode("CASH_VS_GL_SIMPLE")
                .description("Cash ledger vs GL ingestion using SDK example")
                .addBatch(cashBatch)
                .addBatch(glBatch)
                .build();

        pipeline.run(scenario);
        LOGGER.info("Successfully submitted example batches using ingestion SDK");
    }

    private IngestionBatch buildCashBatch() {
        JdbcCsvBatchBuilder builder = new JdbcCsvBatchBuilder(jdbcTemplate);
        return builder.build(
                "CASH",
                "cash-ledger",
                CASH_LEDGER_QUERY,
                Map.of(),
                CANONICAL_COLUMNS,
                Map.of());
    }

    private IngestionBatch buildGeneralLedgerBatch() throws IOException {
        RestApiCsvBatchBuilder builder = new RestApiCsvBatchBuilder(restTemplate);
        String body = StreamUtils.copyToString(
                new ClassPathResource("data/gl_source.json").getInputStream(),
                StandardCharsets.UTF_8);

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(body));
            server.start();
            return builder.get(
                    "GL",
                    "general-ledger",
                    server.url("/api/gl").uri(),
                    CANONICAL_COLUMNS,
                    Map.of(),
                    "payload.entries");
        }
    }
}
