package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.TransformationType;
import jakarta.validation.constraints.NotNull;

public record TransformationValidationRequest(
        @NotNull TransformationType type,
        String expression,
        String configuration) {}

