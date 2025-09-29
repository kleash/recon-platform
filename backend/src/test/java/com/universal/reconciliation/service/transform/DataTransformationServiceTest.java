package com.universal.reconciliation.service.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import com.universal.reconciliation.domain.enums.TransformationType;
import com.universal.reconciliation.service.ai.OpenAiClient;
import com.universal.reconciliation.service.ai.OpenAiPromptRequest;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataTransformationServiceTest {

    private DataTransformationService transformationService;

    private StubOpenAiClient openAiClient;

    @BeforeEach
    void setUp() {
        openAiClient = new StubOpenAiClient();
        transformationService = new DataTransformationService(
                new GroovyTransformationEvaluator(),
                new ExcelFormulaTransformationEvaluator(),
                new FunctionPipelineTransformationEvaluator(new ObjectMapper()),
                new OpenAiTransformationEvaluator(openAiClient, new ObjectMapper()));
    }

    @Test
    void validate_llmPromptRequiresTemplate() {
        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setType(TransformationType.LLM_PROMPT);
        transformation.setConfiguration("{}");

        assertThatThrownBy(() -> transformationService.validate(transformation))
                .isInstanceOf(TransformationEvaluationException.class)
                .hasMessageContaining("prompt template");
    }

    @Test
    void applyTransformations_executesGroovyScript() {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setTransformations(new LinkedHashSet<>());

        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setMapping(mapping);
        transformation.setType(TransformationType.GROOVY_SCRIPT);
        transformation.setExpression("return value ? value.toString().reverse() : null");
        transformation.setActive(true);
        mapping.getTransformations().add(transformation);

        Object result = transformationService.applyTransformations(mapping, "ABC123", Map.of());
        assertThat(result).isEqualTo("321CBA");
    }

    @Test
    void applyTransformations_supportsMultiStatementGroovyScriptsWithoutExplicitReturn() {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setTransformations(new LinkedHashSet<>());

        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setMapping(mapping);
        transformation.setType(TransformationType.GROOVY_SCRIPT);
        transformation.setExpression(
                """
                value = value?.trim()
                if (row?.get('boost')) {
                    value = value + row.get('boost')
                }
                """
        );
        transformation.setActive(true);
        mapping.getTransformations().add(transformation);

        Object result = transformationService.applyTransformations(mapping, " code ", Map.of("boost", "-X"));
        assertThat(result).isEqualTo("code-X");
    }

    @Test
    void applyTransformations_executesExcelFormula() {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setTransformations(new LinkedHashSet<>());

        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setMapping(mapping);
        transformation.setType(TransformationType.EXCEL_FORMULA);
        transformation.setExpression("=VALUE & RAW_COL");
        transformation.setActive(true);
        mapping.getTransformations().add(transformation);

        Object result = transformationService.applyTransformations(mapping, "10", Map.of("raw_col", "-suffix"));
        assertThat(result).isEqualTo("10-suffix");
    }

    @Test
    void applyTransformations_executesFunctionPipeline() {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setTransformations(new LinkedHashSet<>());

        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setMapping(mapping);
        transformation.setType(TransformationType.FUNCTION_PIPELINE);
        transformation.setConfiguration("{\"steps\":[{\"function\":\"TRIM\"},{\"function\":\"TO_UPPERCASE\"}]}");
        transformation.setActive(true);
        mapping.getTransformations().add(transformation);

        Object result = transformationService.applyTransformations(mapping, "  hello ", Map.of());
        assertThat(result).isEqualTo("HELLO");
    }

    @Test
    void applyTransformations_executesLlmPrompt() {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setTransformations(new LinkedHashSet<>());

        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setMapping(mapping);
        transformation.setType(TransformationType.LLM_PROMPT);
        transformation.setConfiguration("{" +
                "\"promptTemplate\":\"Return JSON with normalizedValue using {{value}}\"," +
                "\"resultPath\":\"normalizedValue\"}");
        transformation.setActive(true);
        mapping.getTransformations().add(transformation);

        openAiClient.setResponse("{\"normalizedValue\":\"ABC\"}");

        Object result = transformationService.applyTransformations(mapping, "abc", Map.of("currency", "USD"));

        assertThat(result).isEqualTo("ABC");
        assertThat(openAiClient.getLastRequest().prompt()).contains("abc");
    }

    private static class StubOpenAiClient implements OpenAiClient {

        private String response = "{}";
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
