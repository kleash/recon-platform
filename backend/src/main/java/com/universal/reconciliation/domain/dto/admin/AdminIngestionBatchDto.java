package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.DataBatchStatus;
import java.time.Instant;

/**
 * Response returned when an ingestion batch is accepted through the admin API.
 */
public record AdminIngestionBatchDto(
        Long id,
        String label,
        DataBatchStatus status,
        Long recordCount,
        String checksum,
        Instant ingestedAt) {}

