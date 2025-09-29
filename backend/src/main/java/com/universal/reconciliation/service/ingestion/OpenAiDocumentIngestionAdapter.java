package com.universal.reconciliation.service.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.config.OpenAiProperties;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.service.ai.JsonNodePath;
import com.universal.reconciliation.service.ai.OpenAiClient;
import com.universal.reconciliation.service.ai.OpenAiClientException;
import com.universal.reconciliation.service.ai.OpenAiPromptRequest;
import com.universal.reconciliation.service.ai.PromptTemplateRenderer;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Ingestion adapter that extracts text from arbitrary documents (PDF, emails,
 * office documents) using Apache Tika and then delegates structured extraction
 * to OpenAI. The adapter produces column/value maps so the existing canonical
 * projection pipeline can remain unchanged.
 */
@Component
public class OpenAiDocumentIngestionAdapter implements IngestionAdapter {

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
            new TypeReference<>() {};
    private static final String OPTION_PROMPT_TEMPLATE = "promptTemplate";
    private static final String OPTION_EXTRACTION_SCHEMA = "extractionSchema";
    private static final String OPTION_RECORD_PATH = "recordPath";
    private static final String OPTION_MODEL = "model";
    private static final String OPTION_TEMPERATURE = "temperature";
    private static final String OPTION_MAX_TOKENS = "maxOutputTokens";
    private static final String DEFAULT_PROMPT_TEMPLATE = """
            You are a reconciliation ingestion assistant. Extract structured records that match the provided JSON schema.
            Return only valid JSON.

            Schema:
            {{schema}}

            Document:
            {{document}}
            """;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    public OpenAiDocumentIngestionAdapter(OpenAiClient openAiClient, ObjectMapper objectMapper, OpenAiProperties properties) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public IngestionAdapterType getType() {
        return IngestionAdapterType.LLM_DOCUMENT;
    }

    @Override
    public List<Map<String, Object>> readRecords(IngestionAdapterRequest request) {
        String documentText = extractText(request.inputStreamSupplier());
        if (!StringUtils.hasText(documentText)) {
            return List.of();
        }
        LlmOptions options = resolveOptions(request.options());
        String prompt = renderPrompt(documentText, options);
        Map<String, Object> schema = options.schema();
        try {
            String responseJson = openAiClient.completeJson(new OpenAiPromptRequest(
                    options.model(), prompt, schema, options.temperature(), options.maxOutputTokens()));
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode targetNode = navigateToNode(root, options.recordPath());
            if (targetNode == null || targetNode.isMissingNode() || targetNode.isNull()) {
                return List.of();
            }
            List<Map<String, Object>> records = convertNodeToRecords(targetNode);
            return records.stream()
                    .map(record -> enrichRecord(record, documentText, options, prompt))
                    .toList();
        } catch (OpenAiClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse OpenAI response", ex);
        }
    }

    private String extractText(Supplier<InputStream> supplier) {
        AutoDetectParser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        try (InputStream stream = supplier.get()) {
            parser.parse(stream, handler, metadata, new ParseContext());
            String extracted = handler.toString();
            return extracted != null ? extracted : "";
        } catch (SAXException | org.apache.tika.exception.TikaException | java.io.IOException ex) {
            throw new IllegalStateException("Unable to extract text from document", ex);
        }
    }

    private LlmOptions resolveOptions(Map<String, Object> options) {
        if (options == null) {
            options = Collections.emptyMap();
        }
        String promptTemplate = asText(options.get(OPTION_PROMPT_TEMPLATE)).orElse(DEFAULT_PROMPT_TEMPLATE);
        Map<String, Object> schema = parseSchema(options.get(OPTION_EXTRACTION_SCHEMA));
        String recordPath = asText(options.get(OPTION_RECORD_PATH)).orElse(null);
        String model = asText(options.get(OPTION_MODEL)).orElse(null);
        Double temperature = asDouble(options.get(OPTION_TEMPERATURE)).orElse(null);
        Integer maxTokens = asInteger(options.get(OPTION_MAX_TOKENS)).orElse(null);
        return new LlmOptions(promptTemplate, schema, recordPath, model, temperature, maxTokens);
    }

    private String renderPrompt(String documentText, LlmOptions options) {
        String truncatedDocument = truncate(documentText, properties.getDocumentCharacterLimit());
        Map<String, String> substitutions = new LinkedHashMap<>();
        substitutions.put("document", truncatedDocument);
        substitutions.put("schema", options.schema() != null ? toPrettyJson(options.schema()) : "(not provided)");
        return PromptTemplateRenderer.render(options.promptTemplate(), substitutions);
    }

    private List<Map<String, Object>> convertNodeToRecords(JsonNode node) throws JsonProcessingException {
        if (node.isArray()) {
            return objectMapper.convertValue(node, LIST_OF_MAPS);
        }
        if (node.isObject()) {
            Map<String, Object> asMap = objectMapper.convertValue(node, MAP_TYPE_REFERENCE);
            return List.of(asMap);
        }
        if (node.isTextual()) {
            JsonNode parsed = objectMapper.readTree(node.asText());
            return convertNodeToRecords(parsed);
        }
        throw new IllegalStateException("OpenAI response does not contain a JSON object or array");
    }

    private Map<String, Object> enrichRecord(Map<String, Object> record, String documentText, LlmOptions options, String prompt) {
        Map<String, Object> enriched = new LinkedHashMap<>(record);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("model", Optional.ofNullable(options.model()).orElse(properties.getDefaultModel()));
        metadata.put("recordPath", options.recordPath());
        metadata.put("promptTemplate", options.promptTemplate());
        metadata.put("documentPreview", truncate(documentText, properties.getMetadataPreviewCharacters()));
        metadata.put("promptCharacters", prompt != null ? prompt.length() : 0);
        enriched.put("_llm", metadata);
        return enriched;
    }

    private JsonNode navigateToNode(JsonNode root, String recordPath) {
        if (!StringUtils.hasText(recordPath)) {
            return root;
        }
        return JsonNodePath.navigate(root, recordPath);
    }

    private Map<String, Object> parseSchema(Object rawSchema) {
        if (rawSchema instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (rawSchema instanceof String schemaText) {
            String trimmed = schemaText.trim();
            if (!trimmed.isEmpty()) {
                try {
                    JsonNode node = objectMapper.readTree(trimmed);
                    return objectMapper.convertValue(node, MAP_TYPE_REFERENCE);
                } catch (JsonProcessingException ex) {
                    throw new IllegalArgumentException("Invalid extraction schema JSON", ex);
                }
            }
        }
        return null;
    }

    private Optional<String> asText(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
        }
        return Optional.of(value.toString());
    }

    private Optional<Double> asDouble(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        return asText(value).map(text -> {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Temperature must be numeric", ex);
            }
        });
    }

    private Optional<Integer> asInteger(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        return asText(value).map(text -> {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("maxOutputTokens must be numeric", ex);
            }
        });
    }

    private String truncate(String text, int maxCharacters) {
        if (text == null || text.length() <= maxCharacters) {
            return text;
        }
        return text.substring(0, Math.max(0, maxCharacters));
    }

    private String toPrettyJson(Map<String, Object> schema) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (JsonProcessingException ex) {
            return schema.toString();
        }
    }

    private record LlmOptions(
            String promptTemplate,
            Map<String, Object> schema,
            String recordPath,
            String model,
            Double temperature,
            Integer maxOutputTokens) {}
}

