package com.universal.reconciliation.service.transform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import com.universal.reconciliation.domain.enums.TransformationType;
import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataTransformationServiceTest {

    private DataTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new DataTransformationService(
                new GroovyTransformationEvaluator(),
                new ExcelFormulaTransformationEvaluator(),
                new FunctionPipelineTransformationEvaluator(new ObjectMapper()));
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
}

