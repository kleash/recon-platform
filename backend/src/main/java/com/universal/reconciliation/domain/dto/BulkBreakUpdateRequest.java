package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.BreakStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Represents a bulk update request containing a collection of break identifiers and shared actions.
 */
public record BulkBreakUpdateRequest(
        @NotEmpty List<Long> breakIds,
        BreakStatus status,
        String comment,
        String action,
        String correlationId) {

    public boolean hasStatusChange() {
        return status != null;
    }

    public boolean hasComment() {
        return comment != null && !comment.isBlank();
    }

    public String trimmedComment() {
        return hasComment() ? comment.trim() : null;
    }

    public String resolvedAction() {
        return (action == null || action.isBlank()) ? "BULK_NOTE" : action;
    }

    @AssertTrue(message = "Bulk update requires a status change or comment")
    public boolean hasWork() {
        return hasStatusChange() || hasComment();
    }
}
