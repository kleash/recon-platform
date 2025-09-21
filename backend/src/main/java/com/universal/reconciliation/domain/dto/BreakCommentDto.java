package com.universal.reconciliation.domain.dto;

import java.time.Instant;

/**
 * Projects the comment timeline for a break item.
 */
public record BreakCommentDto(Long id, String actorDn, String action, String comment, Instant createdAt) {
}
