package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.ExportFormat;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Request payload to queue a dataset export job.
 */
public record ExportJobRequestDto(
        @NotNull ExportFormat format,
        Map<String, List<String>> filters,
        String fileNamePrefix,
        boolean includeMetadata) {}

