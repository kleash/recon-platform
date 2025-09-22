package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.BreakStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to change a break's status.
 */
public record UpdateBreakStatusRequest(@NotNull BreakStatus status) {
}
