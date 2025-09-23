package com.universal.reconciliation.service.ingestion;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Wrapper for adapter-specific input. Using a supplier allows the adapter to
 * control resource lifecycle.
 */
public record IngestionAdapterRequest(
        Supplier<InputStream> inputStreamSupplier,
        Map<String, Object> options) {}
