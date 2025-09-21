package com.universal.reconciliation.domain.dto;

/**
 * Request used to manually trigger the matching engine.
 */
public record TriggerRunRequest(String comments) {
}
