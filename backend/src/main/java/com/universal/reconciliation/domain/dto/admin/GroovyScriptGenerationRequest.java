package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.FieldDataType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record GroovyScriptGenerationRequest(
        @NotBlank String prompt,
        @NotBlank String fieldName,
        FieldDataType fieldDataType,
        String sourceCode,
        String sourceColumn,
        Object sampleValue,
        Map<String, Object> rawRecord) {}
