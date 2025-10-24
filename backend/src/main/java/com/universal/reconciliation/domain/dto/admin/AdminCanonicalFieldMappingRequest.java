package com.universal.reconciliation.domain.dto.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request payload describing how a canonical field maps to a source column.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AdminCanonicalFieldMappingRequest(
        Long id,
        @NotBlank String sourceCode,
        @NotBlank String sourceColumn,
        String defaultValue,
        String sourceDateFormat,
        String targetDateFormat,
        Integer ordinalPosition,
        boolean required,
        List<AdminCanonicalFieldTransformationRequest> transformations) {}
