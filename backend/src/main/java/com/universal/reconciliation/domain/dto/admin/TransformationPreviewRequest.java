package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.TransformationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record TransformationPreviewRequest(
        Object value,
        Map<String, Object> rawRecord,
        @NotNull List<@Valid PreviewTransformationDto> transformations) {

    public record PreviewTransformationDto(
            @NotNull TransformationType type,
            String expression,
            String configuration,
            Integer displayOrder,
            Boolean active) {}
}

