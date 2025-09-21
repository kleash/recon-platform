package com.universal.reconciliation.domain.dto;

/**
 * DTO representing a reconciliation that is accessible to the current user.
 */
public record ReconciliationListItemDto(Long id, String code, String name, String description) {
}
