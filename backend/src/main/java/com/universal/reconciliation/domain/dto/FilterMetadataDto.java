package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.BreakStatus;
import java.util.List;

/**
 * Describes the filter options available to the current user for a
 * reconciliation run.
 */
public record FilterMetadataDto(
        List<String> products,
        List<String> subProducts,
        List<String> entities,
        List<BreakStatus> statuses) {
}

