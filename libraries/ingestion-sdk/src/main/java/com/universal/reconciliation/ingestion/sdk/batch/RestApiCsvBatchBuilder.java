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
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Calls an HTTP API that returns a JSON array and converts it into a CSV-backed batch.
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

    public IngestionBatch get(String sourceCode, String label, URI uri, List<String> columns, Map<String, Object> options) throws IOException {
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        return exchange(sourceCode, label, request, columns, options);
    }

    public IngestionBatch exchange(String sourceCode, String label, RequestEntity<?> request, List<String> columns, Map<String, Object> options) throws IOException {
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IOException("API call failed with status " + response.getStatusCode());
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        if (!root.isArray()) {
            throw new IOException("Expected JSON array response but received: " + root.getNodeType());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode node : root) {
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
}
