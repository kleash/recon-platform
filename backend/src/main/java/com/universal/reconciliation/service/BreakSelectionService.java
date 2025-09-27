package com.universal.reconciliation.service;

import com.universal.reconciliation.service.search.BreakSearchCriteria;
import com.universal.reconciliation.service.search.BreakSearchCursor;
import com.universal.reconciliation.service.search.BreakSearchResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Aggregates break identifiers for server-side bulk operations.
 */
@Service
public class BreakSelectionService {

    private final BreakSearchService breakSearchService;

    public BreakSelectionService(BreakSearchService breakSearchService) {
        this.breakSearchService = breakSearchService;
    }

    public BreakSelectionResult collectBreakIds(
            Long reconciliationId, BreakSearchCriteria criteria, List<String> userGroups) {
        BreakSearchCriteria current = sanitise(criteria);
        List<Long> ids = new ArrayList<>();
        long totalCount = -1;

        while (true) {
            BreakSearchResult page = breakSearchService.search(reconciliationId, current, userGroups);
            page.rows().forEach(row -> ids.add(row.breakId()));
            if (totalCount < 0 && page.totalCount() >= 0) {
                totalCount = page.totalCount();
            }
            if (!page.hasMore() || page.nextCursor() == null) {
                break;
            }
            current = withCursor(current, page.nextCursor());
        }

        if (totalCount < 0) {
            totalCount = ids.size();
        }

        return new BreakSelectionResult(ids, totalCount);
    }

    private BreakSearchCriteria sanitise(BreakSearchCriteria criteria) {
        int adjustedSize = Math.min(1000, Math.max(criteria.pageSize(), 500));
        return new BreakSearchCriteria(
                criteria.fromDate(),
                criteria.toDate(),
                criteria.runIds(),
                criteria.triggerTypes(),
                criteria.statuses(),
                criteria.columnFilters(),
                criteria.searchTerm(),
                adjustedSize,
                null,
                false);
    }

    private BreakSearchCriteria withCursor(BreakSearchCriteria base, BreakSearchCursor cursor) {
        return new BreakSearchCriteria(
                base.fromDate(),
                base.toDate(),
                base.runIds(),
                base.triggerTypes(),
                base.statuses(),
                base.columnFilters(),
                base.searchTerm(),
                base.pageSize(),
                cursor,
                false);
    }

    public record BreakSelectionResult(List<Long> breakIds, long totalCount) {}
}

