package com.universal.reconciliation.domain.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request payload describing a report template.
 */
public record AdminReportTemplateRequest(
        Long id,
        @NotBlank String name,
        @NotBlank String description,
        boolean includeMatched,
        boolean includeMismatched,
        boolean includeMissing,
        boolean highlightDifferences,
        List<@Valid AdminReportColumnRequest> columns) {}

