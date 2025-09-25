package com.universal.reconciliation.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload describing how a canonical field maps to a source column.
 */
public record AdminCanonicalFieldMappingRequest(
        Long id,
        @NotBlank String sourceCode,
        @NotBlank String sourceColumn,
        String transformationExpression,
        String defaultValue,
        Integer ordinalPosition,
        boolean required) {}

