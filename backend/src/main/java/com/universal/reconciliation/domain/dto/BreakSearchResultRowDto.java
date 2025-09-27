package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.enums.TriggerType;
import java.time.Instant;
import java.util.Map;

/**
 * Represents a single row returned by the break search API.
 */
public record BreakSearchResultRowDto(
        Long breakId,
        Long runId,
        Instant runDateTime,
        String timezone,
        TriggerType triggerType,
        BreakItemDto breakItem,
        Map<String, String> attributes) {}

