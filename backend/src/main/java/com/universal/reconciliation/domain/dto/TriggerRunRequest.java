package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.TriggerType;

/**
 * Request used to trigger the matching engine via API.
 */
public record TriggerRunRequest(
        TriggerType triggerType, String correlationId, String comments, String initiatedBy) {
}
