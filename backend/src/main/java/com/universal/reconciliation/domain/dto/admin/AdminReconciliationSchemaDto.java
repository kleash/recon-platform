package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Schema export payload consumed by ETL teams.
 */
public record AdminReconciliationSchemaDto(
        Long definitionId,
        String code,
        String name,
        List<SchemaSourceDto> sources,
        List<SchemaFieldDto> fields) {

    public record SchemaSourceDto(
            String code,
            IngestionAdapterType adapterType,
            boolean anchor,
            String connectionConfig,
            String arrivalExpectation,
            String arrivalTimezone,
            Integer arrivalSlaMinutes,
            String adapterOptions,
            List<SchemaSourceFieldDto> schemaFields,
            String ingestionEndpoint) {}

    public record SchemaSourceFieldDto(
            String name,
            String displayName,
            FieldDataType dataType,
            boolean required,
            String description) {}

    public record SchemaFieldDto(
            String displayName,
            String canonicalName,
            FieldRole role,
            FieldDataType dataType,
            ComparisonLogic comparisonLogic,
            BigDecimal thresholdPercentage,
            String formattingHint,
            boolean required,
            List<SchemaFieldMappingDto> mappings) {}

    public record SchemaFieldMappingDto(
            String sourceCode,
            String sourceColumn,
            String defaultValue,
            Integer ordinalPosition,
            boolean required,
            List<SchemaFieldTransformationDto> transformations) {}

    public record SchemaFieldTransformationDto(
            Long id,
            String type,
            String expression,
            String configuration,
            Integer displayOrder,
            boolean active) {}
}
