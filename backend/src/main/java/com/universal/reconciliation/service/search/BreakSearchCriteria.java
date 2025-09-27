package com.universal.reconciliation.service.search;

import com.universal.reconciliation.domain.dto.BreakColumnFilterDto;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.TriggerType;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Input criteria for the run/break search service. All filters are optional;
 * the service will apply sensible defaults (e.g. last 24h) when omitted.
 */
public record BreakSearchCriteria(
        Instant fromDate,
        Instant toDate,
        Set<Long> runIds,
        Set<TriggerType> triggerTypes,
        Set<BreakStatus> statuses,
        Map<String, BreakColumnFilterDto> columnFilters,
        String searchTerm,
        int pageSize,
        BreakSearchCursor cursor,
        boolean includeTotals) {

    public BreakSearchCriteria {
        if (pageSize <= 0 || pageSize > 1000) {
            pageSize = Math.min(Math.max(pageSize, 1), 1000);
        }
        columnFilters = columnFilters == null ? Collections.emptyMap() : Map.copyOf(columnFilters);
        runIds = runIds == null ? Collections.emptySet() : Set.copyOf(runIds);
        triggerTypes = triggerTypes == null ? Collections.emptySet() : Set.copyOf(triggerTypes);
        statuses = statuses == null ? Collections.emptySet() : Set.copyOf(statuses);
    }

    public boolean hasColumnFilters() {
        return !columnFilters.isEmpty();
    }

    public List<BreakColumnFilterDto> columnFilterValues() {
        return List.copyOf(columnFilters.values());
    }
}

