package com.universal.reconciliation.service.transform;

import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import com.universal.reconciliation.domain.enums.TransformationType;
import java.util.Comparator;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Central orchestrator used by ingestion flows and preview endpoints to
 * execute configured transformation rules.
 */
@Service
public class DataTransformationService {

    private final GroovyTransformationEvaluator groovyEvaluator;
    private final ExcelFormulaTransformationEvaluator excelEvaluator;
    private final FunctionPipelineTransformationEvaluator pipelineEvaluator;
    public DataTransformationService(
            GroovyTransformationEvaluator groovyEvaluator,
            ExcelFormulaTransformationEvaluator excelEvaluator,
            FunctionPipelineTransformationEvaluator pipelineEvaluator) {
        this.groovyEvaluator = groovyEvaluator;
        this.excelEvaluator = excelEvaluator;
        this.pipelineEvaluator = pipelineEvaluator;
    }

    public Object applyTransformations(CanonicalFieldMapping mapping, Object value, Map<String, Object> rawRecord) {
        if (mapping.getTransformations() == null || mapping.getTransformations().isEmpty()) {
            return value;
        }
        Map<String, Object> safeRecord = rawRecord == null ? Map.of() : rawRecord;
        Object current = value;
        for (CanonicalFieldTransformation transformation : mapping.getTransformations().stream()
                .sorted(Comparator.comparing(CanonicalFieldTransformation::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList()) {
            if (!transformation.isActive()) {
                continue;
            }
            TransformationType type = transformation.getType();
            if (type == null) {
                continue;
            }
            current = switch (type) {
                case GROOVY_SCRIPT -> groovyEvaluator.evaluate(transformation, current, safeRecord);
                case EXCEL_FORMULA -> excelEvaluator.evaluate(transformation, current, safeRecord);
                case FUNCTION_PIPELINE -> pipelineEvaluator.evaluate(transformation, current, safeRecord);
            };
        }
        return current;
    }

    public void validate(CanonicalFieldTransformation transformation) {
        TransformationType type = transformation.getType();
        if (type == null) {
            throw new TransformationEvaluationException("Transformation type is required");
        }
        switch (type) {
            case GROOVY_SCRIPT -> groovyEvaluator.validateExpression(transformation.getExpression());
            case EXCEL_FORMULA -> excelEvaluator.validateExpression(transformation.getExpression());
            case FUNCTION_PIPELINE -> pipelineEvaluator.validateConfiguration(transformation.getConfiguration());
        }
    }

    public Object evaluateGroovyScript(String script, Object value, Map<String, Object> rawRecord) {
        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setType(TransformationType.GROOVY_SCRIPT);
        transformation.setExpression(script);
        transformation.setActive(true);
        return groovyEvaluator.evaluate(transformation, value, rawRecord == null ? Map.of() : rawRecord);
    }

    public void validateGroovyScript(String script) {
        groovyEvaluator.validateExpression(script);
    }
}
