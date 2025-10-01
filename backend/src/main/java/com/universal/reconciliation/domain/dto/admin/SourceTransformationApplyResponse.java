package com.universal.reconciliation.domain.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * Returns the dataset after applying a transformation plan.
 */
public record SourceTransformationApplyResponse(List<Map<String, Object>> transformedRows) {}
