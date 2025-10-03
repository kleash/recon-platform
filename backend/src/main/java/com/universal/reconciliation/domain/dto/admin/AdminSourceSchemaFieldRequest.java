package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.FieldDataType;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload used when persisting source schema fields via the admin API.
 */
public record AdminSourceSchemaFieldRequest(
        @NotBlank String name,
        String displayName,
        FieldDataType dataType,
        boolean required,
        String description) {}
