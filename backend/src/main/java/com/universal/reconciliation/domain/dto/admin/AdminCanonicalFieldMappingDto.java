package com.universal.reconciliation.domain.dto.admin;

import java.util.List;

/**
 * Mapping metadata linking canonical fields to concrete source columns.
 */

public record AdminCanonicalFieldMappingDto(
        Long id,
        String sourceCode,
        String sourceColumn,
        String defaultValue,
        String sourceDateFormat,
        String targetDateFormat,
        Integer ordinalPosition,
        boolean required,
        List<AdminCanonicalFieldTransformationDto> transformations) {}
