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
        Map<String, String> classifications,
        List<BreakStatus> allowedStatusTransitions,
        Instant detectedAt,
        Map<String, Map<String, Object>> sources,
        List<String> missingSources,
        List<BreakCommentDto> comments,
        List<BreakHistoryEntryDto> history,
        String submittedByDn,
        String submittedByGroup,
        Instant submittedAt) {
}
