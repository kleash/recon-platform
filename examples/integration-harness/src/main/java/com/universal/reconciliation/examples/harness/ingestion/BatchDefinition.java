package com.universal.reconciliation.examples.harness.ingestion;

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
}
