package com.universal.reconciliation.domain.dto;

import java.util.List;

/**
 * Response payload for APIs that return only break identifiers for bulk
 * operations.
 */
public record BreakSelectionResponseDto(List<Long> breakIds, long totalCount) {}

