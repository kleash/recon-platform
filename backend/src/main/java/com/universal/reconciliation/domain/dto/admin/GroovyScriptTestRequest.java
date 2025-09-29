package com.universal.reconciliation.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record GroovyScriptTestRequest(
        @NotBlank String script,
        Object value,
        Map<String, Object> rawRecord) {}
