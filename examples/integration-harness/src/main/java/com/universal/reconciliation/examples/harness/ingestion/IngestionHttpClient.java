package com.universal.reconciliation.examples.harness.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class IngestionHttpClient implements AutoCloseable {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType CSV = MediaType.get("text/csv");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final CliOptions options;
    private final String baseUrl;
    private final Map<String, Long> definitionCache;
    private String token;

    IngestionHttpClient(CliOptions options) throws IOException {
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();
        this.options = options;
        this.baseUrl = normalizeBaseUrl(options.baseUrl());
        this.definitionCache = new HashMap<>();
        this.token = authenticate();
    }

    void runScenario(ScenarioDefinition scenario) throws IOException {
        System.out.printf("Scenario '%s' (%s) – ingesting %d batch(es)%n",
                scenario.key(), scenario.reconciliationCode(), scenario.batches().size());

        long definitionId = resolveDefinitionId(scenario.reconciliationCode());
        for (BatchDefinition batch : scenario.batches()) {
            ingestBatch(definitionId, batch);
        }
    }

    private String authenticate() throws IOException {
        Map<String, String> payload = Map.of(
                "username", options.username(),
                "password", options.password());
        RequestBody body = RequestBody.create(mapper.writeValueAsBytes(payload), JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/auth/login")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Authentication failed with status " + response.code());
            }
            JsonNode tree = mapper.readTree(Objects.requireNonNull(response.body()).bytes());
            String tokenValue = textOrNull(tree, "token");
            if (tokenValue == null || tokenValue.isBlank()) {
                throw new IOException("Authentication response did not include a token");
            }
            System.out.printf("Authenticated as %s%n", options.username());
            return tokenValue;
        }
    }

    private long resolveDefinitionId(String reconciliationCode) throws IOException {
        Long cached = definitionCache.get(reconciliationCode);
        if (cached != null) {
            return cached;
        }

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl + "/api/admin/reconciliations"))
                .newBuilder()
                .addQueryParameter("search", reconciliationCode)
                .addQueryParameter("size", "50")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) {
                token = authenticate();
                return resolveDefinitionId(reconciliationCode);
            }
            if (!response.isSuccessful()) {
                throw new IOException("Failed to discover reconciliation '" + reconciliationCode + "' (status "
                        + response.code() + ")");
            }
            JsonNode tree = mapper.readTree(Objects.requireNonNull(response.body()).bytes());
            JsonNode items = tree.path("items");
            if (!items.isArray()) {
                throw new IOException("Admin API response did not include items array while searching for "
                        + reconciliationCode);
            }
            for (JsonNode item : items) {
                if (reconciliationCode.equalsIgnoreCase(textOrNull(item, "code"))) {
                    long id = item.path("id").asLong(-1);
                    if (id <= 0) {
                        throw new IOException("Retrieved invalid reconciliation id for code " + reconciliationCode);
                    }
                    definitionCache.put(reconciliationCode, id);
                    return id;
                }
            }
        }

        throw new IOException("Reconciliation code '" + reconciliationCode + "' is not present. Ensure it was configured via admin API.");
    }

    private void ingestBatch(long reconciliationId, BatchDefinition batch) throws IOException {
        byte[] payload = readResource(batch.resourcePath());
        String filename = extractFileName(batch.resourcePath());

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("adapterType", "CSV_FILE");
        if (batch.label() != null && !batch.label().isBlank()) {
            metadataMap.put("label", batch.label());
        }
        if (batch.options() != null && !batch.options().isEmpty()) {
            metadataMap.put("options", batch.options());
        }

        RequestBody metadataBody = RequestBody.create(mapper.writeValueAsBytes(metadataMap), JSON);
        RequestBody fileBody = RequestBody.create(payload, CSV);

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", "metadata.json", metadataBody)
                .addFormDataPart("file", filename, fileBody)
                .build();

        String url = String.format(Locale.ROOT,
                "%s/api/admin/reconciliations/%d/sources/%s/batches",
                baseUrl,
                reconciliationId,
                batch.sourceCode());

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) {
                token = authenticate();
                ingestBatch(reconciliationId, batch);
                return;
            }
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "<no response>";
                throw new IOException(String.format(
                        Locale.ROOT,
                        "Failed to ingest %s for source %s (status %d): %s",
                        batch.label(),
                        batch.sourceCode(),
                        response.code(),
                        errorBody));
            }
            JsonNode result = mapper.readTree(Objects.requireNonNull(response.body()).bytes());
            String status = textOrNull(result, "status");
            long recordCount = result.path("recordCount").asLong(-1);
            System.out.printf("  ✔ %s -> source %s (%s, %d records)%n",
                    batch.label(),
                    batch.sourceCode(),
                    status == null ? "UNKNOWN" : status,
                    recordCount);
        }
    }

    private byte[] readResource(String resourcePath) throws IOException {
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(normalized)) {
            if (stream == null) {
                throw new IOException("Unable to locate resource on classpath: " + resourcePath);
            }
            return stream.readAllBytes();
        }
    }

    private static String extractFileName(String resourcePath) {
        int index = resourcePath.lastIndexOf('/') + 1;
        if (index <= 0 || index >= resourcePath.length()) {
            return "payload.csv";
        }
        return resourcePath.substring(index);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @Override
    public void close() {
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
    }
}
