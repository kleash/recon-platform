package com.universal.reconciliation.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.config.OpenAiProperties;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DefaultOpenAiClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OpenAiProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new OpenAiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://localhost");
        properties.setDefaultModel("gpt-test");
        properties.setDefaultTemperature(0.1d);
        properties.setDefaultMaxOutputTokens(256);

        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    }

    @Test
    void completeJsonReturnsTrimmedAssistantContent() {
        DefaultOpenAiClient client = new DefaultOpenAiClient(properties, builder);
        OpenAiPromptRequest request = new OpenAiPromptRequest(
                null,
                "  give json  ",
                Map.of("type", "object", "properties", Map.of("foo", Map.of("type", "number"))),
                null,
                null);

        Map<String, Object> messagePayload = Map.of(
                "role", "assistant",
                "content", "  {\n    \"foo\": 1\n  }  ");
        Map<String, Object> responsePayload = Map.of("message", messagePayload);
        String responseJson = json(Map.of("choices", List.of(responsePayload)));

        server.expect(requestTo("http://localhost/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("  give json  "))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.foo.type").value("number"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        String response = client.completeJson(request);

        assertThat(response).isEqualTo("{\n    \"foo\": 1\n  }");
        server.verify();
    }

    @Test
    void completeJsonFallsBackToJsonObjectFormatWhenSchemaMissing() {
        DefaultOpenAiClient client = new DefaultOpenAiClient(properties, builder);
        OpenAiPromptRequest request = new OpenAiPromptRequest("gpt-override", "respond", null, 0.33d, 444);

        server.expect(requestTo("http://localhost/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("gpt-override"))
                .andExpect(jsonPath("$.temperature").value(0.33d))
                .andExpect(jsonPath("$.max_tokens").value(444))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"answer\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.completeJson(request)).isEqualTo("answer");
        server.verify();
    }

    @Test
    void completeJsonThrowsWhenApiKeyMissing() {
        OpenAiProperties noKeyProps = new OpenAiProperties();
        noKeyProps.setBaseUrl("http://localhost");
        DefaultOpenAiClient client = new DefaultOpenAiClient(noKeyProps, RestClient.builder());

        assertThatThrownBy(() -> client.completeJson(new OpenAiPromptRequest(null, "prompt", null, null, null)))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void completeJsonThrowsWhenPromptMissing() {
        DefaultOpenAiClient client = new DefaultOpenAiClient(properties, builder);

        assertThatThrownBy(() -> client.completeJson(new OpenAiPromptRequest(null, "  ", null, null, null)))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessageContaining("Prompt must be provided");
    }

    @Test
    void completeJsonThrowsWhenOpenAiReturnsEmptyChoices() {
        DefaultOpenAiClient client = new DefaultOpenAiClient(properties, builder);
        server.expect(requestTo("http://localhost/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.completeJson(new OpenAiPromptRequest(null, "prompt", null, null, null)))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessageContaining("empty response");
        server.verify();
    }

    @Test
    void completeJsonWrapsRestClientResponseExceptions() {
        DefaultOpenAiClient client = new DefaultOpenAiClient(properties, builder);
        server.expect(requestTo("http://localhost/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"bad request\"}"));

        assertThatThrownBy(() -> client.completeJson(new OpenAiPromptRequest(null, "prompt", null, null, null)))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessageContaining("status 400")
                .hasMessageContaining("bad request");
        server.verify();
    }

    @Test
    void completeJsonWrapsGenericRestClientExceptions() {
        DefaultOpenAiClient client = new DefaultOpenAiClient(properties, builder);
        server.expect(requestTo("http://localhost/v1/chat/completions"))
                .andRespond(withException(new IOException("boom")));

        assertThatThrownBy(() -> client.completeJson(new OpenAiPromptRequest(null, "prompt", null, null, null)))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessageContaining("OpenAI request failed");
        server.verify();
    }

    private static String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize test JSON", ex);
        }
    }
}
