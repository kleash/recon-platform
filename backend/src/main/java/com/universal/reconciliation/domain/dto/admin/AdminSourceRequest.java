package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Source definition used when creating or updating reconciliations.
 */
public record AdminSourceRequest(
        Long id,
        @NotBlank String code,
        @NotBlank String displayName,
        @NotNull IngestionAdapterType adapterType,
        boolean anchor,
        String description,
        String connectionConfig,
        String arrivalExpectation,
        String arrivalTimezone,
        Integer arrivalSlaMinutes,
        String adapterOptions,
        SourceTransformationPlan transformationPlan) {}
