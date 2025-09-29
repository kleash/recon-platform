package com.universal.reconciliation.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.universal.reconciliation.config.OpenAiProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Default {@link OpenAiClient} backed by the OpenAI REST API. Requests are
 * delegated to the chat completions endpoint so callers can optionally supply
 * JSON schemas for deterministic extraction.
 */
@Component
public class DefaultOpenAiClient implements OpenAiClient {

    private static final String COMPLETION_URI = "/v1/chat/completions";

    private final OpenAiProperties properties;
    private final RestClient restClient;

    public DefaultOpenAiClient(OpenAiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public String completeJson(OpenAiPromptRequest request) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new OpenAiClientException("OpenAI API key is not configured.");
        }
        if (request == null || !StringUtils.hasText(request.prompt())) {
            throw new OpenAiClientException("Prompt must be provided when calling OpenAI.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        String model = StringUtils.hasText(request.model()) ? request.model() : properties.getDefaultModel();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", request.prompt())));
        Double temperature = request.temperature() != null ? request.temperature() : properties.getDefaultTemperature();
        body.put("temperature", temperature);
        Integer maxTokens = request.maxOutputTokens() != null
                ? request.maxOutputTokens()
                : properties.getDefaultMaxOutputTokens();
        body.put("max_tokens", maxTokens);

        Map<String, Object> schema = request.jsonSchema();
        if (schema != null && !schema.isEmpty()) {
            body.put("response_format", Map.of(
                    "type",
                    "json_schema",
                    "json_schema",
                    Map.of("name", "structured_output", "schema", schema)));
        } else {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            OpenAiChatCompletionResponse response = restClient
                    .post()
                    .uri(COMPLETION_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OpenAiChatCompletionResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new OpenAiClientException("OpenAI returned an empty response.");
            }
            String combined = response.choices().stream()
                    .filter(Objects::nonNull)
                    .map(OpenAiChatCompletionChoice::message)
                    .filter(Objects::nonNull)
                    .map(OpenAiChatMessage::content)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.joining("\n"))
                    .trim();
            if (!StringUtils.hasText(combined)) {
                throw new OpenAiClientException("OpenAI returned an empty response.");
            }
            return combined;
        } catch (RestClientResponseException ex) {
            String message = String.format(
                    "OpenAI request failed with status %s: %s",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new OpenAiClientException(message, ex);
        } catch (RestClientException ex) {
            throw new OpenAiClientException("OpenAI request failed: " + ex.getMessage(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiChatCompletionResponse(List<OpenAiChatCompletionChoice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiChatCompletionChoice(OpenAiChatMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiChatMessage(String role, String content) {}
}

