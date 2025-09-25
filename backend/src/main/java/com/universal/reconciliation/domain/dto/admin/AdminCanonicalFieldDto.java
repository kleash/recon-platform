package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Canonical field metadata captured for admin editing.
 */
public record AdminCanonicalFieldDto(
        Long id,
        String canonicalName,
        String displayName,
        FieldRole role,
        FieldDataType dataType,
        ComparisonLogic comparisonLogic,
        BigDecimal thresholdPercentage,
        String classifierTag,
        String formattingHint,
        Integer displayOrder,
        boolean required,
        Instant createdAt,
        Instant updatedAt,
        List<AdminCanonicalFieldMappingDto> mappings) {}

