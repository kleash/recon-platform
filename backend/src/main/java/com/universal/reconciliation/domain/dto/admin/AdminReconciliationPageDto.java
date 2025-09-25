package com.universal.reconciliation.domain.dto.admin;

import java.util.List;

/**
 * Paginated response wrapper for reconciliation catalog listings.
 */
public record AdminReconciliationPageDto(
        List<AdminReconciliationSummaryDto> items,
        long totalElements,
        int totalPages,
        int page,
        int size) {}
