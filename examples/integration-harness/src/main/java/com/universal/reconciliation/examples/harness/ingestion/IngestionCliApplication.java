package com.universal.reconciliation.examples.harness.ingestion;

import java.io.IOException;
import java.util.List;

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

        try (IngestionHttpClient client = new IngestionHttpClient(options)) {
            for (ScenarioDefinition scenario : scenarios) {
                client.runScenario(scenario);
            }
            System.out.println("Ingestion completed successfully");
        } catch (IOException ex) {
            System.err.println("Ingestion failed: " + ex.getMessage());
            System.exit(1);
        }
    }
}
