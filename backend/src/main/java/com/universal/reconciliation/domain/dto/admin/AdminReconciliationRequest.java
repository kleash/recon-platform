package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Payload used to create or update reconciliation definitions through the
 * admin API.
 */
public record AdminReconciliationRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String description,
        String owner,
        boolean makerCheckerEnabled,
        String notes,
        @NotNull ReconciliationLifecycleStatus status,
        boolean autoTriggerEnabled,
        String autoTriggerCron,
        String autoTriggerTimezone,
        Integer autoTriggerGraceMinutes,
        Long version,
        @NotEmpty List<@Valid AdminSourceRequest> sources,
        @NotEmpty List<@Valid AdminCanonicalFieldRequest> canonicalFields,
        List<@Valid AdminReportTemplateRequest> reportTemplates,
        List<@Valid AdminAccessControlEntryRequest> accessControlEntries) {}

