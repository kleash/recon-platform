package com.universal.reconciliation.domain.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * Result of inferring a schema from a sample dataset.
 */
public record SourceSchemaInferenceResponse(
        List<AdminSourceSchemaFieldDto> fields,
        List<Map<String, Object>> sampleRows) {}
