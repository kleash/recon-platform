package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request metadata submitted alongside ingestion batch payloads.
 */
public record AdminIngestionRequest(
        @NotNull IngestionAdapterType adapterType,
        Map<String, Object> options,
        String label) {}

