package com.universal.reconciliation.domain.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * Provides the raw and transformed datasets generated during preview.
 */
public record SourceTransformationPreviewResponse(
        List<Map<String, Object>> rawRows,
        List<Map<String, Object>> transformedRows) {}
