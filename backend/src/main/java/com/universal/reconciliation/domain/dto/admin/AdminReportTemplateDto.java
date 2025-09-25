package com.universal.reconciliation.domain.dto.admin;

import java.util.List;

/**
 * Report template metadata for administrative consumption.
 */
public record AdminReportTemplateDto(
        Long id,
        String name,
        String description,
        boolean includeMatched,
        boolean includeMismatched,
        boolean includeMissing,
        boolean highlightDifferences,
        List<AdminReportColumnDto> columns) {}

