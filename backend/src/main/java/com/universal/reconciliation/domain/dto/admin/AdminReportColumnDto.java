package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ReportColumnSource;

/**
 * Column definition for an administrative report template view.
 */
public record AdminReportColumnDto(
        Long id,
        String header,
        ReportColumnSource source,
        String sourceField,
        int displayOrder,
        boolean highlightDifferences) {}

