package com.universal.reconciliation.domain.dto;

import java.util.List;

/**
 * Response payload for the break search endpoint.
 */
public record BreakSearchResponseDto(
        List<BreakSearchResultRowDto> rows,
        BreakSearchPageInfoDto page,
        List<GridColumnDto> columns) {}

