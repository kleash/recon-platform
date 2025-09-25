package com.universal.reconciliation.domain.dto.admin;

/**
 * Mapping metadata linking canonical fields to concrete source columns.
 */
public record AdminCanonicalFieldMappingDto(
        Long id,
        String sourceCode,
        String sourceColumn,
        String transformationExpression,
        String defaultValue,
        Integer ordinalPosition,
        boolean required) {}

