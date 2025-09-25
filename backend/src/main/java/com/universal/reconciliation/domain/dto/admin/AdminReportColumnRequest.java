package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ReportColumnSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Column payload for report template authoring.
 */
public record AdminReportColumnRequest(
        Long id,
        @NotBlank String header,
        @NotNull ReportColumnSource source,
        String sourceField,
        int displayOrder,
        boolean highlightDifferences) {}

