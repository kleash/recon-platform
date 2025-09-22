package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents the data required for the UI to display break details.
 */
public record BreakItemDto(
        Long id,
        BreakType breakType,
        BreakStatus status,
        String product,
        String subProduct,
        String entity,
        List<BreakStatus> allowedStatusTransitions,
        Instant detectedAt,
        Map<String, Object> sourceA,
        Map<String, Object> sourceB,
        List<BreakCommentDto> comments) {
}
