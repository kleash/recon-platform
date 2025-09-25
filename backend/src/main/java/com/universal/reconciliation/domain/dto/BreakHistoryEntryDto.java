package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import java.time.Instant;

/**
 * Timeline entry combining free-form comments and workflow audit events.
 */
public record BreakHistoryEntryDto(
        EntryType type,
        String actorDn,
        AccessRole actorRole,
        String action,
        String comment,
        BreakStatus previousStatus,
        BreakStatus newStatus,
        Instant occurredAt,
        String correlationId) {

    public enum EntryType {
        COMMENT,
        WORKFLOW
    }
}
