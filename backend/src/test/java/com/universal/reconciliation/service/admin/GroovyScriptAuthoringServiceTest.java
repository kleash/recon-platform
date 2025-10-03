package com.universal.reconciliation.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.config.OpenAiProperties;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationResponse;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.GroovyScriptGenerationScope;
import com.universal.reconciliation.service.ai.OpenAiClient;
import com.universal.reconciliation.service.ai.OpenAiClientException;
import com.universal.reconciliation.service.ai.OpenAiPromptRequest;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroovyScriptAuthoringServiceTest {

    @Mock
    private OpenAiClient openAiClient;

    private ObjectMapper objectMapper;
    private OpenAiProperties properties;
    private GroovyScriptAuthoringService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new OpenAiProperties();
        properties.setDocumentCharacterLimit(800);
        service = new GroovyScriptAuthoringService(openAiClient, objectMapper, properties);
    }

    @Test
    void generateBuildsPromptWithContextAndParsesResponse() {
        GroovyScriptGenerationRequest request = new GroovyScriptGenerationRequest(
                "Normalize the trade amount to two decimals",
                "Net Amount",
                FieldDataType.DECIMAL,
                "CUSTODY",
                "net_amount",
                123.4567,
                Map.of("net_amount", "123.4567", "currency", "USD"),
                List.of("net_amount", "currency"),
                GroovyScriptGenerationScope.FIELD);

        when(openAiClient.completeJson(any()))
                .thenReturn("{\"script\":\"return value\",\"summary\":\"Rounds to two decimals\"}");

        GroovyScriptGenerationResponse response = service.generate(request);

        assertThat(response.script()).isEqualTo("return value");
        assertThat(response.summary()).isEqualTo("Rounds to two decimals");

        ArgumentCaptor<OpenAiPromptRequest> promptCaptor = ArgumentCaptor.forClass(OpenAiPromptRequest.class);
        verify(openAiClient).completeJson(promptCaptor.capture());
        OpenAiPromptRequest promptRequest = promptCaptor.getValue();
        assertThat(promptRequest.temperature()).isEqualTo(0.15d);
        assertThat(promptRequest.maxOutputTokens()).isEqualTo(600);
        String prompt = promptRequest.prompt();
        assertThat(prompt)
                .contains("Net Amount")
                .contains("Field data type: DECIMAL")
                .contains("Source column: net_amount")
                .contains("Administrator request:")
                .contains("123.4567")
                .contains("currency");
    }

    @Test
    void generateStripsCodeFenceFromResponse() {
        GroovyScriptGenerationRequest request = new GroovyScriptGenerationRequest(
                "Trim whitespace",
                "Trade Reference",
                FieldDataType.STRING,
                null,
                null,
                "  TR-123  ",
                Map.of(),
                List.of("trade_ref"),
                GroovyScriptGenerationScope.FIELD);

        when(openAiClient.completeJson(any()))
                .thenReturn("{\"script\":\"```groovy\\nreturn value?.toString()?.trim()\\n```\"}");

        GroovyScriptGenerationResponse response = service.generate(request);

        assertThat(response.script()).isEqualTo("return value?.toString()?.trim()");
        assertThat(response.summary()).isNull();
    }

    @Test
    void generateThrowsWhenScriptMissing() {
        GroovyScriptGenerationRequest request = new GroovyScriptGenerationRequest(
                "Do something",
                "Field",
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                GroovyScriptGenerationScope.FIELD);

        when(openAiClient.completeJson(any())).thenReturn("{\"summary\":\"Nothing\"}");

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(TransformationEvaluationException.class)
                .hasMessageContaining("did not include a Groovy script");
    }

    @Test
    void generateWrapsOpenAiErrors() {
        GroovyScriptGenerationRequest request = new GroovyScriptGenerationRequest(
                "Something",
                "Field",
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                GroovyScriptGenerationScope.FIELD);

        when(openAiClient.completeJson(any())).thenThrow(new OpenAiClientException("API key missing"));

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(TransformationEvaluationException.class)
                .hasMessageContaining("API key missing");
    }

    @Test
    void datasetScopeIncludesAvailableColumnsInPrompt() {
        GroovyScriptGenerationRequest request = new GroovyScriptGenerationRequest(
                "Remove rows where status is IGNORED",
                null,
                null,
                "PAYMENTS",
                null,
                null,
                Map.of("status", "IGNORED", "amount", "10"),
                List.of("status", "amount", "reference"),
                GroovyScriptGenerationScope.DATASET);

        when(openAiClient.completeJson(any()))
                .thenReturn("{\"script\":\"rows.removeIf { it.status == 'IGNORED' }\"}");

        service.generate(request);

        ArgumentCaptor<OpenAiPromptRequest> promptCaptor = ArgumentCaptor.forClass(OpenAiPromptRequest.class);
        verify(openAiClient).completeJson(promptCaptor.capture());
        String prompt = promptCaptor.getValue().prompt();
        assertThat(prompt)
                .contains("Dataset context")
                .contains("status, amount, reference")
                .contains("Example row")
                .contains("Administrator request");
    }
}
