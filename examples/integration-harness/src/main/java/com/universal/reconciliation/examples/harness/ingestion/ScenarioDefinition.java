package com.universal.reconciliation.examples.harness.ingestion;

import java.util.List;

record ScenarioDefinition(String key, String reconciliationCode, List<BatchDefinition> batches) {

    ScenarioDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Scenario key must not be blank");
        }
        if (reconciliationCode == null || reconciliationCode.isBlank()) {
            throw new IllegalArgumentException("Reconciliation code must not be blank");
        }
        if (batches == null || batches.isEmpty()) {
            throw new IllegalArgumentException("Scenario must define at least one batch");
        }
    }
}
