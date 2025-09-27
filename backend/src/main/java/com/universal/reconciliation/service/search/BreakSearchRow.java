package com.universal.reconciliation.service.search;

import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.enums.TriggerType;
import java.time.Instant;
import java.util.Map;

/**
 * Represents a single row in the break search grid, combining the base break
 * payload with run metadata.
 */
public record BreakSearchRow(
        Long breakId,
        Long runId,
        Instant runDateTime,
        String runDateTimeZone,
        TriggerType triggerType,
        BreakItemDto breakItem,
        Map<String, String> attributeValues) {}

