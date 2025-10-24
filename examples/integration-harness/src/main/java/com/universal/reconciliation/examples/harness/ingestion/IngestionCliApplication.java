package com.universal.reconciliation.examples.harness.ingestion;

import com.universal.reconciliation.ingestion.sdk.IngestionPipeline;
import com.universal.reconciliation.ingestion.sdk.ReconciliationIngestionClient;
import java.io.IOException;
import java.util.List;

/**
 * Lightweight entry point that replays predefined ingestion scenarios against a running platform instance.
 * Keeps console output intentional so CI logs remain readable when multiple scenarios execute sequentially.
 */
public final class IngestionCliApplication {

    private IngestionCliApplication() {
        // no instances
    }

    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);
        List<ScenarioDefinition> scenarios;
        try {
            scenarios = ScenarioRegistry.resolve(options.scenario());
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
            return;
        }

        try (ReconciliationIngestionClient client = new ReconciliationIngestionClient(
                options.baseUrl(),
                options.username(),
                options.password())) {
            IngestionPipeline pipeline = new IngestionPipeline(client);
            for (ScenarioDefinition scenario : scenarios) {
                pipeline.run(scenario.toScenario());
            }
            System.out.println("Ingestion completed successfully");
        } catch (IOException ex) {
            System.err.println("Ingestion failed: " + ex.getMessage());
            System.exit(1);
        }
    }
}
