package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.TransformationType;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload supplied by admin UI to describe a transformation rule.
 */
public record AdminCanonicalFieldTransformationRequest(
        Long id,
        @NotNull TransformationType type,
        String expression,
        String configuration,
        Integer displayOrder,
        Boolean active) {}

