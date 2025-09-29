package com.universal.reconciliation.ingestion.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight HTTP client that authenticates against the platform and streams batches using the admin ingestion APIs.
 */
public class ReconciliationIngestionClient implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationIngestionClient.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final Map<String, Long> definitionCache;

    private volatile String token;

    public ReconciliationIngestionClient(OkHttpClient client, String baseUrl, String username, String password) {
        this.client = Objects.requireNonNull(client, "client");
        this.mapper = new ObjectMapper();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.definitionCache = new ConcurrentHashMap<>();
    }

    public ReconciliationIngestionClient(String baseUrl, String username, String password) {
        this(new OkHttpClient(), baseUrl, username, password);
    }

    public long resolveDefinitionId(String reconciliationCode) throws IOException {
        Objects.requireNonNull(reconciliationCode, "reconciliationCode");
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
                .header("Authorization", "Bearer " + ensureToken())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) {
                token = authenticate();
                return resolveDefinitionId(reconciliationCode);
            }
            if (!response.isSuccessful()) {
                throw new IOException("Failed to look up reconciliation '" + reconciliationCode + "' (status " + response.code() + ")");
            }
            JsonNode tree = mapper.readTree(Objects.requireNonNull(response.body()).bytes());
            JsonNode items = tree.path("items");
            for (JsonNode item : items) {
                if (reconciliationCode.equalsIgnoreCase(item.path("code").asText())) {
                    long id = item.path("id").asLong(-1);
                    if (id <= 0) {
                        throw new IOException("Invalid id returned for reconciliation '" + reconciliationCode + "'");
                    }
                    definitionCache.put(reconciliationCode, id);
                    return id;
                }
            }
        }

        throw new IOException("Reconciliation code '" + reconciliationCode + "' not found. Ensure it is published via admin API.");
    }

    public IngestionResult ingestBatch(long reconciliationId, IngestionBatch batch) throws IOException {
        Objects.requireNonNull(batch, "batch");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("adapterType", deriveAdapterType(batch));
        if (batch.getLabel() != null && !batch.getLabel().isBlank()) {
            metadata.put("label", batch.getLabel());
        }
        if (!batch.getOptions().isEmpty()) {
            metadata.put("options", batch.getOptions());
        }

        RequestBody metadataBody = RequestBody.create(mapper.writeValueAsBytes(metadata), JSON);
        MediaType contentType = MediaType.get(batch.getMediaType());
        RequestBody fileBody = RequestBody.create(batch.getPayload(), contentType);

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", "metadata.json", metadataBody)
                .addFormDataPart("file", buildFilename(batch), fileBody)
                .build();

        String url = String.format(Locale.ROOT,
                "%s/api/admin/reconciliations/%d/sources/%s/batches",
                baseUrl,
                reconciliationId,
                batch.getSourceCode());

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + ensureToken())
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) {
                token = authenticate();
                return ingestBatch(reconciliationId, batch);
            }
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "<no body>";
                throw new IOException(String.format(Locale.ROOT,
                        "Ingestion failed for source %s (%s): %s",
                        batch.getSourceCode(),
                        response.code(),
                        responseBody));
            }

            JsonNode result = mapper.readTree(Objects.requireNonNull(response.body()).bytes());
            return new IngestionResult(result.path("status").asText("UNKNOWN"), result.path("recordCount").asLong(0));
        }
    }

    private String ensureToken() throws IOException {
        String current = token;
        if (current == null || current.isBlank()) {
            current = authenticate();
            token = current;
        }
        return current;
    }

    private String authenticate() throws IOException {
        Map<String, String> payload = Map.of(
                "username", username,
                "password", password);
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
            String tokenValue = tree.path("token").asText();
            if (tokenValue == null || tokenValue.isBlank()) {
                throw new IOException("Authentication response did not include a token");
            }
            LOGGER.info("Authenticated user '{}' against reconciliation platform", username);
            return tokenValue;
        }
    }

    private static String buildFilename(IngestionBatch batch) {
        String label = batch.getLabel();
        if (label == null || label.isBlank()) {
            return batch.getSourceCode() + "-batch";
        }
        return label.replaceAll("[^A-Za-z0-9._-]", "_") + ".csv";
    }

    private static String deriveAdapterType(IngestionBatch batch) {
        if (batch.getMediaType().toLowerCase(Locale.ROOT).contains("json")) {
            return "JSON_FILE";
        }
        return "CSV_FILE";
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

    public record IngestionResult(String status, long recordCount) {
    }
}
