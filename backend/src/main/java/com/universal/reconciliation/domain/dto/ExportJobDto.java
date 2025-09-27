package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.ExportFormat;
import com.universal.reconciliation.domain.enums.ExportJobStatus;
import com.universal.reconciliation.domain.enums.ExportJobType;
import java.time.Instant;

/**
 * Lightweight representation of an export job for listing.
 */
public record ExportJobDto(
        Long id,
        ExportJobType jobType,
        ExportFormat format,
        ExportJobStatus status,
        String fileName,
        String contentHash,
        Long rowCount,
        Instant createdAt,
        Instant completedAt,
        String errorMessage) {}

