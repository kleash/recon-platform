package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Canonical field payload for reconciliation authoring.
 */
public record AdminCanonicalFieldRequest(
        Long id,
        @NotBlank String canonicalName,
        @NotBlank String displayName,
        @NotNull FieldRole role,
        @NotNull FieldDataType dataType,
        @NotNull ComparisonLogic comparisonLogic,
        BigDecimal thresholdPercentage,
        String classifierTag,
        String formattingHint,
        Integer displayOrder,
        boolean required,
        List<@Valid AdminCanonicalFieldMappingRequest> mappings) {}

