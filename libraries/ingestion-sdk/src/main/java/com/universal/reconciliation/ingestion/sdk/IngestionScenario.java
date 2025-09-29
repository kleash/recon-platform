package com.universal.reconciliation.ingestion.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Groups a set of batches destined for a particular reconciliation definition.
 */
public final class IngestionScenario {

    private final String key;
    private final String reconciliationCode;
    private final String description;
    private final List<IngestionBatch> batches;

    private IngestionScenario(Builder builder) {
        this.key = Objects.requireNonNull(builder.key, "key");
        this.reconciliationCode = Objects.requireNonNull(builder.reconciliationCode, "reconciliationCode");
        this.description = builder.description;
        this.batches = List.copyOf(builder.batches);
        if (this.batches.isEmpty()) {
            throw new IllegalArgumentException("Scenario must contain at least one batch");
        }
    }

    public String getKey() {
        return key;
    }

    public String getReconciliationCode() {
        return reconciliationCode;
    }

    public String getDescription() {
        return description;
    }

    public List<IngestionBatch> getBatches() {
        return Collections.unmodifiableList(batches);
    }

    public static Builder builder(String key) {
        return new Builder().key(key);
    }

    public static final class Builder {
        private String key;
        private String reconciliationCode;
        private String description;
        private final List<IngestionBatch> batches = new ArrayList<>();

        private Builder() {
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder reconciliationCode(String reconciliationCode) {
            this.reconciliationCode = reconciliationCode;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addBatch(IngestionBatch batch) {
            this.batches.add(Objects.requireNonNull(batch, "batch"));
            return this;
        }

        public Builder addBatches(List<IngestionBatch> batches) {
            this.batches.addAll(batches);
            return this;
        }

        public IngestionScenario build() {
            return new IngestionScenario(this);
        }
    }
}
