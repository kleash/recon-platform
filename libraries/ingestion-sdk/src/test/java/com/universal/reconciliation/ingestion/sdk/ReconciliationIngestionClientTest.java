package com.universal.reconciliation.ingestion.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class ReconciliationIngestionClientTest {

    @Test
    void resolveDefinitionIdCachesLookupAndToken() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"token\":\"token-1\"}"));
            server.enqueue(jsonResponse("{\"items\":[{\"code\":\"CASH\",\"id\":101}]}"));
            server.start();

            String baseUrl = server.url("/").toString();
            try (ReconciliationIngestionClient client = new ReconciliationIngestionClient(new OkHttpClient(), baseUrl, "user", "pass")) {
                long id1 = client.resolveDefinitionId("CASH");
                long id2 = client.resolveDefinitionId("CASH");

                assertThat(id1).isEqualTo(101L);
                assertThat(id2).isEqualTo(101L);
                assertThat(server.getRequestCount()).isEqualTo(2);

                RecordedRequest login = server.takeRequest();
                assertThat(login.getPath()).isEqualTo("/api/auth/login");
                RecordedRequest lookup = server.takeRequest();
                assertThat(lookup.getPath()).contains("/api/admin/reconciliations");
                assertThat(lookup.getHeader("Authorization")).isEqualTo("Bearer token-1");
            }
        }
    }

    @Test
    void resolveDefinitionIdRetriesOn401() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"token\":\"initial\"}"));
            server.enqueue(new MockResponse().setResponseCode(401));
            server.enqueue(jsonResponse("{\"token\":\"refreshed\"}"));
            server.enqueue(jsonResponse("{\"items\":[{\"code\":\"CASH\",\"id\":202}]}"));
            server.start();

            String baseUrl = server.url("/").toString();
            try (ReconciliationIngestionClient client = new ReconciliationIngestionClient(new OkHttpClient(), baseUrl, "user", "pass")) {
                long id = client.resolveDefinitionId("CASH");
                assertThat(id).isEqualTo(202L);

                RecordedRequest login1 = server.takeRequest();
                assertThat(login1.getPath()).isEqualTo("/api/auth/login");
                RecordedRequest search1 = server.takeRequest();
                assertThat(search1.getHeader("Authorization")).isEqualTo("Bearer initial");
                RecordedRequest login2 = server.takeRequest();
                assertThat(login2.getPath()).isEqualTo("/api/auth/login");
                RecordedRequest search2 = server.takeRequest();
                assertThat(search2.getHeader("Authorization")).isEqualTo("Bearer refreshed");
            }
        }
    }

    @Test
    void ingestBatchRetriesOn401() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"token\":\"token-1\"}"));
            server.enqueue(new MockResponse().setResponseCode(401));
            server.enqueue(jsonResponse("{\"token\":\"token-2\"}"));
            server.enqueue(jsonResponse("{\"status\":\"ACCEPTED\",\"recordCount\":2}"));
            server.start();

            String baseUrl = server.url("/").toString();
            try (ReconciliationIngestionClient client = new ReconciliationIngestionClient(new OkHttpClient(), baseUrl, "user", "pass")) {
                IngestionBatch batch = IngestionBatch.builder("SRC", "example")
                        .mediaType("text/csv")
                        .payload("id\n1".getBytes(StandardCharsets.UTF_8))
                        .options(Map.of())
                        .build();

                ReconciliationIngestionClient.IngestionResult result = client.ingestBatch(55L, batch);
                assertThat(result.status()).isEqualTo("ACCEPTED");
                assertThat(result.recordCount()).isEqualTo(2L);

                RecordedRequest login1 = server.takeRequest();
                assertThat(login1.getPath()).isEqualTo("/api/auth/login");
                RecordedRequest ingest1 = server.takeRequest();
                assertThat(ingest1.getHeader("Authorization")).isEqualTo("Bearer token-1");
                RecordedRequest login2 = server.takeRequest();
                assertThat(login2.getPath()).isEqualTo("/api/auth/login");
                RecordedRequest ingest2 = server.takeRequest();
                assertThat(ingest2.getHeader("Authorization")).isEqualTo("Bearer token-2");
            }
        }
    }

    @Test
    void ingestBatchSurfacesErrorResponse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"token\":\"token-1\"}"));
            server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
            server.start();

            String baseUrl = server.url("/").toString();
            try (ReconciliationIngestionClient client = new ReconciliationIngestionClient(new OkHttpClient(), baseUrl, "user", "pass")) {
                IngestionBatch batch = IngestionBatch.builder("SRC", "example")
                        .mediaType("text/csv")
                        .payload("id\n1".getBytes(StandardCharsets.UTF_8))
                        .options(Map.of())
                        .build();

                assertThatThrownBy(() -> client.ingestBatch(77L, batch))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("500");
            }
        }
    }

    @Test
    void closeShutsDownOkHttpResources() {
        OkHttpClient okHttpClient = new OkHttpClient();
        try (ReconciliationIngestionClient client =
                new ReconciliationIngestionClient(okHttpClient, "http://localhost:8080", "user", "pass")) {
            // no-op
        }
        assertThat(okHttpClient.dispatcher().executorService().isShutdown()).isTrue();
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
