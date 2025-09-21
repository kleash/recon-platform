package com.universal.reconciliation.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload used to append a comment to a break.
 */
public record AddBreakCommentRequest(
        @NotBlank(message = "Comment cannot be empty") String comment,
        @NotBlank(message = "Action is required") String action) {
}
