package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.FieldDataType;

/**
 * Describes a single column within a source schema definition.
 */
public record AdminSourceSchemaFieldDto(
        String name,
        String displayName,
        FieldDataType dataType,
        boolean required,
        String description) {}
