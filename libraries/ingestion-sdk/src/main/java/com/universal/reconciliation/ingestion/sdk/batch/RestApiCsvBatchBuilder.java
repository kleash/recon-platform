package com.universal.reconciliation.ingestion.sdk.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Calls an HTTP API that returns a JSON payload and converts the record array into a CSV-backed batch.
 */
public final class RestApiCsvBatchBuilder {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RestApiCsvBatchBuilder(RestTemplate restTemplate) {
        this(restTemplate, new ObjectMapper());
    }

    public RestApiCsvBatchBuilder(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public IngestionBatch get(String sourceCode, String label, URI uri, List<String> columns, Map<String, Object> options)
            throws IOException {
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        return exchange(sourceCode, label, request, columns, options);
    }

    public IngestionBatch get(
            String sourceCode,
            String label,
            URI uri,
            List<String> columns,
            Map<String, Object> options,
            String recordPointer)
            throws IOException {
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        return exchange(sourceCode, label, request, columns, options, recordPointer);
    }

    public IngestionBatch get(
            String sourceCode,
            String label,
            URI uri,
            List<String> columns,
            Map<String, Object> options,
            Function<JsonNode, Iterable<JsonNode>> recordExtractor)
            throws IOException {
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        return exchange(sourceCode, label, request, columns, options, recordExtractor);
    }

    public IngestionBatch exchange(
            String sourceCode,
            String label,
            RequestEntity<?> request,
            List<String> columns,
            Map<String, Object> options)
            throws IOException {
        return exchange(sourceCode, label, request, columns, options, (String) null);
    }

    public IngestionBatch exchange(
            String sourceCode,
            String label,
            RequestEntity<?> request,
            List<String> columns,
            Map<String, Object> options,
            String recordPointer)
            throws IOException {
        return exchange(sourceCode, label, request, columns, options, recordPointer, null);
    }

    public IngestionBatch exchange(
            String sourceCode,
            String label,
            RequestEntity<?> request,
            List<String> columns,
            Map<String, Object> options,
            Function<JsonNode, Iterable<JsonNode>> recordExtractor)
            throws IOException {
        return exchange(sourceCode, label, request, columns, options, null, recordExtractor);
    }

    private IngestionBatch exchange(
            String sourceCode,
            String label,
            RequestEntity<?> request,
            List<String> columns,
            Map<String, Object> options,
            String recordPointer,
            Function<JsonNode, Iterable<JsonNode>> recordExtractor)
            throws IOException {
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IOException("API call failed with status " + response.getStatusCode());
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        Iterable<JsonNode> records = selectRecords(root, recordPointer, recordExtractor);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode node : records) {
            Map<String, Object> map = objectMapper.convertValue(node, Map.class);
            rows.add(CsvRenderer.normalizeKeys(map));
        }
        byte[] payload = CsvRenderer.render(rows, columns == null ? null : new ArrayList<>(columns));
        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payload(payload)
                .options(options == null ? Map.of() : options)
                .build();
    }

    private Iterable<JsonNode> selectRecords(
            JsonNode root, String recordPointer, Function<JsonNode, Iterable<JsonNode>> recordExtractor) throws IOException {
        if (recordExtractor != null) {
            Iterable<JsonNode> extracted = recordExtractor.apply(root);
            if (extracted == null) {
                throw new IOException("Record extractor returned null for response body");
            }
            return extracted;
        }

        if (recordPointer != null && !recordPointer.isBlank()) {
            JsonNode pointerNode = root.at(normalizePointer(recordPointer));
            if (pointerNode.isMissingNode()) {
                throw new IOException("JSON pointer '" + recordPointer + "' did not resolve to any node");
            }
            if (!pointerNode.isArray()) {
                throw new IOException(
                        "JSON pointer '" + recordPointer + "' resolved to " + pointerNode.getNodeType() + " instead of array");
            }
            return pointerNode;
        }

        if (!root.isArray()) {
            throw new IOException("Expected JSON array response but received: " + root.getNodeType());
        }
        return root;
    }

    private static String normalizePointer(String pointer) {
        String trimmed = pointer.trim();
        if (trimmed.isEmpty()) {
            return "/";
        }
        if (trimmed.startsWith("/")) {
            return trimmed;
        }
        String[] parts = trimmed.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append('/')
                    .append(part.replace("~", "~0").replace("/", "~1"));
        }
        return builder.length() > 0 ? builder.toString() : "/";
    }
}
