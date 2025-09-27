package com.universal.reconciliation.domain.dto;

/**
 * Pagination metadata for the break search API.
 */
public record BreakSearchPageInfoDto(String nextCursor, boolean hasMore, long totalCount) {}

