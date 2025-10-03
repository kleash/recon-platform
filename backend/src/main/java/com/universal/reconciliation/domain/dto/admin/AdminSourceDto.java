package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import java.time.Instant;
import java.util.List;

/**
 * Represents a data source definition exposed through the admin APIs.
 */
public record AdminSourceDto(
        Long id,
        String code,
        String displayName,
        IngestionAdapterType adapterType,
        boolean anchor,
        String description,
        String connectionConfig,
        String arrivalExpectation,
        String arrivalTimezone,
        Integer arrivalSlaMinutes,
        String adapterOptions,
        SourceTransformationPlan transformationPlan,
        List<AdminSourceSchemaFieldDto> schemaFields,
        List<String> availableColumns,
        Instant createdAt,
        Instant updatedAt) {}
