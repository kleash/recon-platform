package com.universal.reconciliation.ingestion.sdk;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates ingestion for one or more scenarios using the {@link ReconciliationIngestionClient}.
 */
public class IngestionPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionPipeline.class);

    private final ReconciliationIngestionClient client;

    public IngestionPipeline(ReconciliationIngestionClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public void run(IngestionScenario scenario) throws IOException {
        Objects.requireNonNull(scenario, "scenario");
        LOGGER.info("Running ingestion scenario '{}' for reconciliation '{}'", scenario.getKey(), scenario.getReconciliationCode());
        long reconciliationId = client.resolveDefinitionId(scenario.getReconciliationCode());
        for (IngestionBatch batch : scenario.getBatches()) {
            ReconciliationIngestionClient.IngestionResult result = client.ingestBatch(reconciliationId, batch);
            LOGGER.info("Submitted batch '{}' for source '{}' (status={}, recordCount={})",
                    batch.getLabel(), batch.getSourceCode(), result.status(), result.recordCount());
        }
    }

    public void runAll(List<IngestionScenario> scenarios) throws IOException {
        for (IngestionScenario scenario : scenarios) {
            run(scenario);
        }
    }
}
