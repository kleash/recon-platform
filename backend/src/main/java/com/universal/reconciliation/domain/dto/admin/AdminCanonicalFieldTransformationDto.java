package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.TransformationType;

/**
 * Represents a persisted transformation rule exposed to admin clients.
 */
public record AdminCanonicalFieldTransformationDto(
        Long id,
        TransformationType type,
        String expression,
        String configuration,
        Integer displayOrder,
        boolean active) {}

