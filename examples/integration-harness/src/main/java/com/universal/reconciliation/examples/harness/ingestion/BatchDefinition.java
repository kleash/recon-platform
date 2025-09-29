package com.universal.reconciliation.examples.harness.ingestion;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import com.universal.reconciliation.ingestion.sdk.batch.ClasspathCsvBatchLoader;
import java.io.IOException;
import java.util.Map;

record BatchDefinition(String sourceCode, String resourcePath, String label, Map<String, Object> options) {

    BatchDefinition {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("sourceCode must not be blank");
        }
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath must not be blank");
        }
    }

    IngestionBatch toBatch() throws IOException {
        return ClasspathCsvBatchLoader.load(sourceCode, label, resourcePath, options == null ? Map.of() : options);
    }
}
