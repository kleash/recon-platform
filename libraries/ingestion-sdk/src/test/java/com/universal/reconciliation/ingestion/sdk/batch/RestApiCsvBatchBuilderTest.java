package com.universal.reconciliation.ingestion.sdk.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class RestApiCsvBatchBuilderTest {

    @Test
    void convertsJsonArrayToCsv() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[{\"transactionId\":\"T-1\",\"amount\":100.25},{\"transactionId\":\"T-2\",\"amount\":50.75}]"));
            server.start();

            RestApiCsvBatchBuilder builder = new RestApiCsvBatchBuilder(new RestTemplate());
            URI uri = server.url("/transactions").uri();
            IngestionBatch batch = builder.get(
                    "API",
                    "api-batch",
                    uri,
                    List.of("transactionId", "amount"),
                    Map.of());

            String csv = readCsv(batch);
            assertThat(csv).contains("transactionId,amount");
            assertThat(csv).contains("T-1,100.25");
            assertThat(csv).contains("T-2,50.75");
        }
    }

    @Test
    void supportsNestedJsonViaPointer() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"data\":{\"items\":[{\"transactionId\":\"T-3\",\"amount\":5.0}]}}"));
            server.start();

            RestApiCsvBatchBuilder builder = new RestApiCsvBatchBuilder(new RestTemplate());
            IngestionBatch batch = builder.get(
                    "API",
                    "nested",
                    server.url("/nested").uri(),
                    List.of("transactionId", "amount"),
                    Map.of(),
                    "data.items");

            String csv = readCsv(batch);
            assertThat(csv).contains("transactionId,amount");
            assertThat(csv).contains("T-3,5.0");
        }
    }

    @Test
    void failsFastWhenPointerMissing() {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"data\":{\"items\":[]}}"));
            server.start();

            RestApiCsvBatchBuilder builder = new RestApiCsvBatchBuilder(new RestTemplate());
            assertThatThrownBy(() -> builder.get(
                            "API",
                            "missing",
                            server.url("/missing").uri(),
                            List.of(),
                            Map.of(),
                            "data.results"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("data.results");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void supportsCustomRecordExtractor() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"payload\":{\"items\":[{\"transactionId\":\"T-4\",\"amount\":10.0}],\"corrections\":[{\"transactionId\":\"T-5\",\"amount\":3.14}]}}"));
            server.start();

            RestApiCsvBatchBuilder builder = new RestApiCsvBatchBuilder(new RestTemplate());
            IngestionBatch batch = builder.get(
                    "API",
                    "custom",
                    server.url("/custom").uri(),
                    List.of("transactionId", "amount"),
                    Map.of(),
                    (parser, mapper) -> {
                        JsonNode root = mapper.readTree(parser);
                        java.util.List<Map<String, Object>> merged = new java.util.ArrayList<>();
                        root.path("payload").path("items")
                                .forEach(node -> merged.add(mapper.convertValue(node, Map.class)));
                        root.path("payload").path("corrections")
                                .forEach(node -> merged.add(mapper.convertValue(node, Map.class)));
                        return merged.iterator();
                    });

            String csv = readCsv(batch);
            assertThat(csv).contains("T-4,10.0");
            assertThat(csv).contains("T-5,3.14");
        }
    }

    private static String readCsv(IngestionBatch batch) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        batch.writePayload(buffer);
        String csv = buffer.toString(StandardCharsets.UTF_8);
        batch.discardPayload();
        return csv;
    }
}
