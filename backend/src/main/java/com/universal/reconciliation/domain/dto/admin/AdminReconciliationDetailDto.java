package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import java.time.Instant;
import java.util.List;

/**
 * Detailed view of a reconciliation configuration, including nested metadata
 * required for authoring through the admin workspace.
 */
public record AdminReconciliationDetailDto(
        Long id,
        String code,
        String name,
        String description,
        String notes,
        String owner,
        boolean makerCheckerEnabled,
        ReconciliationLifecycleStatus status,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Instant publishedAt,
        String publishedBy,
        Instant retiredAt,
        String retiredBy,
        Long version,
        boolean autoTriggerEnabled,
        String autoTriggerCron,
        String autoTriggerTimezone,
        Integer autoTriggerGraceMinutes,
        List<AdminSourceDto> sources,
        List<AdminCanonicalFieldDto> canonicalFields,
        List<AdminReportTemplateDto> reportTemplates,
        List<AdminAccessControlEntryDto> accessControlEntries,
        List<AdminIngestionBatchDto> ingestionBatches) {}

