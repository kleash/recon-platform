package com.universal.reconciliation.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.config.OpenAiProperties;
import com.universal.reconciliation.service.ai.OpenAiClient;
import com.universal.reconciliation.service.ai.OpenAiPromptRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiDocumentIngestionAdapterTest {

    private OpenAiDocumentIngestionAdapter adapter;
    private StubOpenAiClient openAiClient;
    private OpenAiProperties properties;

    @BeforeEach
    void setUp() {
        openAiClient = new StubOpenAiClient();
        properties = new OpenAiProperties();
        properties.setDocumentCharacterLimit(1000);
        properties.setMetadataPreviewCharacters(200);
        adapter = new OpenAiDocumentIngestionAdapter(openAiClient, new ObjectMapper(), properties);
    }

    @Test
    void readRecords_parsesArrayResponse() {
        openAiClient.setResponse("[{\"invoiceId\":\"INV-1\",\"amount\":\"150.00\"}]");
        IngestionAdapterRequest request = new IngestionAdapterRequest(
                () -> new ByteArrayInputStream("Invoice INV-1 for 150.00".getBytes(StandardCharsets.UTF_8)),
                Map.of("promptTemplate", "Extract JSON from {{document}}"));

        List<Map<String, Object>> records = adapter.readRecords(request);

        assertThat(records).hasSize(1);
        Map<String, Object> record = records.get(0);
        assertThat(record.get("invoiceId")).isEqualTo("INV-1");
        assertThat(record.get("amount")).isEqualTo("150.00");
        assertThat(record).containsKey("_llm");
        assertThat(openAiClient.getLastRequest().prompt()).contains("Invoice INV-1");
    }

    @Test
    void readRecords_usesRecordPath() {
        openAiClient.setResponse("{\"data\":{\"items\":[{\"id\":\"A\"},{\"id\":\"B\"}]}}");
        IngestionAdapterRequest request = new IngestionAdapterRequest(
                () -> new ByteArrayInputStream("Email with attachments".getBytes(StandardCharsets.UTF_8)),
                Map.of(
                        "promptTemplate", "Document {{document}}",
                        "recordPath", "data.items"));

        List<Map<String, Object>> records = adapter.readRecords(request);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("id")).isEqualTo("A");
        assertThat(records.get(1).get("id")).isEqualTo("B");
    }

    private static class StubOpenAiClient implements OpenAiClient {

        private String response = "[]";
        private OpenAiPromptRequest lastRequest;

        @Override
        public String completeJson(OpenAiPromptRequest request) {
            this.lastRequest = request;
            return response;
        }

        void setResponse(String response) {
            this.response = response;
        }

        OpenAiPromptRequest getLastRequest() {
            return lastRequest;
        }
    }
}

