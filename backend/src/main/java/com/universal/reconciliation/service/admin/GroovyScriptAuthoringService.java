package com.universal.reconciliation.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.config.OpenAiProperties;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationResponse;
import com.universal.reconciliation.service.ai.OpenAiClient;
import com.universal.reconciliation.service.ai.OpenAiClientException;
import com.universal.reconciliation.service.ai.OpenAiPromptRequest;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GroovyScriptAuthoringService {

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                    "script",
                    Map.of(
                            "type",
                            "string",
                            "description",
                            "Groovy snippet that can be saved directly without further editing."),
                    "summary",
                    Map.of(
                            "type",
                            "string",
                            "description",
                            "Short explanation of how the script fulfils the requirement.")),
            "required",
            List.of("script"),
            "additionalProperties",
            false);

    private static final double DEFAULT_TEMPERATURE = 0.15d;
    private static final int DEFAULT_MAX_TOKENS = 600;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    public GroovyScriptAuthoringService(
            OpenAiClient openAiClient, ObjectMapper objectMapper, OpenAiProperties properties) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public GroovyScriptGenerationResponse generate(GroovyScriptGenerationRequest request) {
        String prompt = buildPrompt(request);
        try {
            String response = openAiClient.completeJson(new OpenAiPromptRequest(
                    null, prompt, RESPONSE_SCHEMA, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS));
            return parseResponse(response);
        } catch (OpenAiClientException ex) {
            throw new TransformationEvaluationException(ex.getMessage(), ex);
        }
    }

    private GroovyScriptGenerationResponse parseResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode scriptNode = root.get("script");
            if (scriptNode == null || scriptNode.isNull()) {
                throw new TransformationEvaluationException("LLM response did not include a Groovy script.");
            }
            String script = sanitizeScript(scriptNode.asText());
            if (!StringUtils.hasText(script)) {
                throw new TransformationEvaluationException("Generated Groovy script was empty.");
            }
            JsonNode summaryNode = root.get("summary");
            String summary = summaryNode != null && !summaryNode.isNull() ? summaryNode.asText().trim() : null;
            if (!StringUtils.hasText(summary)) {
                summary = null;
            }
            return new GroovyScriptGenerationResponse(script, summary);
        } catch (JsonProcessingException ex) {
            throw new TransformationEvaluationException("Failed to parse LLM response as JSON", ex);
        }
    }

    private String sanitizeScript(String script) {
        if (script == null) {
            return null;
        }
        String trimmed = script.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak >= 0) {
            trimmed = trimmed.substring(firstLineBreak + 1);
        }
        int closingFence = trimmed.lastIndexOf("```");
        if (closingFence >= 0) {
            trimmed = trimmed.substring(0, closingFence);
        }
        return trimmed.trim();
    }

    private String buildPrompt(GroovyScriptGenerationRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are an assistant that writes Groovy snippets for the Universal Reconciliation Platform.\n");
        builder.append("Scripts run inside a sandbox with these bindings: value (current field value) and row/raw (Map of source data).\n");
        builder.append("The script must either return the transformed value or mutate the value binding.\n");
        builder.append("Restrictions: no imports, no class definitions, no printing/logging, and only use methods available on String, Number, Object, and Map.\n");
        builder.append("Always handle nulls defensively and prefer returning the new value.\n\n");

        builder.append("Field context:\n");
        builder.append("- Canonical field: ").append(request.fieldName()).append('\n');
        if (request.fieldDataType() != null) {
            builder.append("- Field data type: ").append(request.fieldDataType().name()).append('\n');
        }
        if (StringUtils.hasText(request.sourceCode())) {
            builder.append("- Source code: ").append(request.sourceCode()).append('\n');
        }
        if (StringUtils.hasText(request.sourceColumn())) {
            builder.append("- Source column: ").append(request.sourceColumn()).append('\n');
        }
        if (request.sampleValue() != null) {
            builder.append("- Example current value: ")
                    .append(renderSampleValue(request.sampleValue()))
                    .append('\n');
        }
        if (request.rawRecord() != null && !request.rawRecord().isEmpty()) {
            builder.append("- Example raw source row (JSON):\n");
            builder.append(renderRawRecord(request.rawRecord())).append('\n');
        }

        builder.append("\nAdministrator request:\n");
        builder.append(request.prompt().trim()).append('\n');

        builder.append("\nRespond with JSON containing the Groovy script and a concise summary.\n");
        builder.append("Ensure the script can be saved directly without additional editing.\n");

        return builder.toString();
    }

    private String renderSampleValue(Object sampleValue) {
        if (sampleValue == null) {
            return "null";
        }
        try {
            String json = objectMapper.writeValueAsString(sampleValue);
            return truncate(json, 120);
        } catch (JsonProcessingException ex) {
            return truncate(String.valueOf(sampleValue), 120);
        }
    }

    private String renderRawRecord(Map<String, Object> rawRecord) {
        try {
            String json = objectMapper.writeValueAsString(rawRecord);
            int limit = Math.max(512, Math.min(properties.getDocumentCharacterLimit(), 4000));
            return truncate(json, limit);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String truncate(String value, int maxCharacters) {
        if (value == null || value.length() <= maxCharacters) {
            return value;
        }
        return value.substring(0, maxCharacters) + "…";
    }
}
