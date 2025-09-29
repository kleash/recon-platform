package com.universal.reconciliation.service.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import com.universal.reconciliation.service.ai.JsonNodePath;
import com.universal.reconciliation.service.ai.OpenAiClient;
import com.universal.reconciliation.service.ai.OpenAiClientException;
import com.universal.reconciliation.service.ai.OpenAiPromptRequest;
import com.universal.reconciliation.service.ai.PromptTemplateRenderer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Evaluates {@link com.universal.reconciliation.domain.enums.TransformationType#LLM_PROMPT}
 * transformations by invoking OpenAI with the configured prompt template.
 */
@Component
class OpenAiTransformationEvaluator {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    OpenAiTransformationEvaluator(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    Object evaluate(CanonicalFieldTransformation transformation, Object currentValue, Map<String, Object> rawRecord) {
        LlmTransformationConfig config = parseConfig(transformation.getConfiguration());
        String promptTemplate = config.promptTemplate();
        if (!StringUtils.hasText(promptTemplate)) {
            throw new TransformationEvaluationException("LLM prompt template is required");
        }

        Map<String, String> substitutions = new LinkedHashMap<>();
        substitutions.put("value", currentValue == null ? "" : currentValue.toString());
        substitutions.put("rawRecord", config.includeRawRecord() ? toJson(rawRecord) : "{}");
        substitutions.put("schema", config.jsonSchema() != null ? toJson(config.jsonSchema()) : "{}");

        String prompt = PromptTemplateRenderer.render(promptTemplate, substitutions);
        Map<String, Object> schema = config.jsonSchema();
        String response;
        try {
            response = openAiClient.completeJson(new OpenAiPromptRequest(
                    config.model(), prompt, schema, config.temperature(), config.maxOutputTokens()));
        } catch (OpenAiClientException ex) {
            throw new TransformationEvaluationException(ex.getMessage(), ex);
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode target = JsonNodePath.navigate(root, config.resultPath());
            if (target == null || target.isMissingNode()) {
                throw new TransformationEvaluationException(
                        "LLM response did not contain the configured result path: " + config.resultPath());
            }
            if (target.isNull()) {
                return null;
            }
            if (target.isValueNode()) {
                return objectMapper.treeToValue(target, Object.class);
            }
            return objectMapper.convertValue(target, Object.class);
        } catch (JsonProcessingException ex) {
            throw new TransformationEvaluationException("Unable to parse LLM response as JSON", ex);
        }
    }

    void validateConfiguration(String configuration) {
        LlmTransformationConfig config = parseConfig(configuration);
        if (!StringUtils.hasText(config.promptTemplate())) {
            throw new TransformationEvaluationException("LLM prompt template is required");
        }
        if (config.jsonSchema() != null) {
            // ensure schema can be serialised
            toJson(config.jsonSchema());
        }
    }

    private LlmTransformationConfig parseConfig(String configuration) {
        if (!StringUtils.hasText(configuration)) {
            return new LlmTransformationConfig(null, null, null, null, null, null, true);
        }
        try {
            JsonNode node = objectMapper.readTree(configuration);
            String promptTemplate = optionalText(node, "promptTemplate");
            Map<String, Object> schema = null;
            if (node.has("jsonSchema") && !node.get("jsonSchema").isNull()) {
                schema = objectMapper.convertValue(node.get("jsonSchema"), Map.class);
            }
            String resultPath = optionalText(node, "resultPath");
            String model = optionalText(node, "model");
            Double temperature = node.has("temperature") && !node.get("temperature").isNull()
                    ? node.get("temperature").asDouble()
                    : null;
            Integer maxTokens = node.has("maxOutputTokens") && !node.get("maxOutputTokens").isNull()
                    ? node.get("maxOutputTokens").asInt()
                    : null;
            boolean includeRawRecord = !node.has("includeRawRecord") || node.get("includeRawRecord").asBoolean(true);
            return new LlmTransformationConfig(
                    promptTemplate, schema, resultPath, model, temperature, maxTokens, includeRawRecord);
        } catch (JsonProcessingException ex) {
            throw new TransformationEvaluationException("Invalid LLM configuration JSON", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new TransformationEvaluationException("Failed to serialize value to JSON", ex);
        }
    }

    private String optionalText(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            String text = node.get(fieldName).asText();
            return text != null && !text.isBlank() ? text.trim() : null;
        }
        return null;
    }

    private record LlmTransformationConfig(
            String promptTemplate,
            Map<String, Object> jsonSchema,
            String resultPath,
            String model,
            Double temperature,
            Integer maxOutputTokens,
            boolean includeRawRecord) {}
}

