package com.universal.reconciliation.ingestion.sdk.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
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
                    .setBody("[{\"transactionId\":\"T-1\",\"amount\":100.25},{\"transactionId\":\"T-2\",\"amount\":50.75}]")
            );
            server.start();

            RestApiCsvBatchBuilder builder = new RestApiCsvBatchBuilder(new RestTemplate());
            URI uri = server.url("/transactions").uri();
            IngestionBatch batch = builder.get(
                    "API",
                    "api-batch",
                    uri,
                    List.of("transactionId", "amount"),
                    Map.of());

            String csv = new String(batch.getPayload(), StandardCharsets.UTF_8);
            assertThat(csv).contains("transactionId,amount");
            assertThat(csv).contains("T-1,100.25");
            assertThat(csv).contains("T-2,50.75");
        }
    }
}
