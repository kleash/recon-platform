package com.universal.reconciliation.service.ai;

import java.util.Map;

/**
 * Simple request payload describing how the OpenAI client should fulfil a
 * structured prompt request.
 */
public record OpenAiPromptRequest(
        String model,
        String prompt,
        Map<String, Object> jsonSchema,
        Double temperature,
        Integer maxOutputTokens) {}

