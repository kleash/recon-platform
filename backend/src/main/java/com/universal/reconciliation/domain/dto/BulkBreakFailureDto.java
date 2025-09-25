package com.universal.reconciliation.domain.dto;

/**
 * Represents a single failure encountered during a bulk maker/checker action.
 */
public record BulkBreakFailureDto(Long breakId, String reason) {}
