package com.universal.reconciliation.domain.dto;

import java.util.List;

/**
 * Wraps the checker approval queue payload for the analyst workspace.
 */
public record ApprovalQueueDto(
        List<BreakItemDto> pendingBreaks,
        FilterMetadataDto filterMetadata) {}

