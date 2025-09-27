package com.universal.reconciliation.service.export;

import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.TriggerType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Simplified representation of a break row for export rendering.
 */
public record DatasetRow(
        Long breakId,
        Long runId,
        Instant runDateTime,
        TriggerType triggerType,
        BreakStatus status,
        String breakType,
        Instant detectedAt,
        Map<String, String> attributes,
        String maker,
        String checker,
        String latestComment,
        List<String> missingSources,
        String submittedBy,
        Instant submittedAt) {}

