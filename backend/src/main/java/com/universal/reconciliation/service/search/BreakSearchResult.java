package com.universal.reconciliation.service.search;

import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.GridColumnDto;
import java.util.List;

/**
 * Encapsulates the rows and pagination metadata returned by the break search
 * service.
 */
public record BreakSearchResult(
        List<BreakSearchRow> rows,
        BreakSearchCursor nextCursor,
        boolean hasMore,
        long totalCount,
        List<GridColumnDto> columns) {}
