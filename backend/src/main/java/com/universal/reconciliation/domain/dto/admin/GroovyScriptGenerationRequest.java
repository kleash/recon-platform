package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.GroovyScriptGenerationScope;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;

public record GroovyScriptGenerationRequest(
        @NotBlank String prompt,
        String fieldName,
        FieldDataType fieldDataType,
        String sourceCode,
        String sourceColumn,
        Object sampleValue,
        Map<String, Object> rawRecord,
        List<String> availableColumns,
        GroovyScriptGenerationScope scope) {}
