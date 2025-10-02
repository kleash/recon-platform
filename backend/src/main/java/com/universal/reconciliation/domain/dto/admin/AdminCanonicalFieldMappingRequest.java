package com.universal.reconciliation.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request payload describing how a canonical field maps to a source column.
 */
public record AdminCanonicalFieldMappingRequest(
        Long id,
        @NotBlank String sourceCode,
        @NotBlank String sourceColumn,
        String transformationExpression,
        String defaultValue,
        String sourceDateFormat,
        String targetDateFormat,
        Integer ordinalPosition,
        boolean required,
        List<AdminCanonicalFieldTransformationRequest> transformations) {}
