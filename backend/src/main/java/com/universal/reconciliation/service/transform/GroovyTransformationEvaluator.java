package com.universal.reconciliation.service.transform;

import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Compiles and executes Groovy snippets inside a sandboxed class-loader.
 */
@Component
class GroovyTransformationEvaluator {

    private final GroovyClassLoader groovyClassLoader;
    private final ConcurrentHashMap<String, Class<? extends Script>> compiledScripts = new ConcurrentHashMap<>();

    GroovyTransformationEvaluator() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(buildSecurityCustomizer());
        this.groovyClassLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), configuration);
    }

    /**
     * Runs the Groovy snippet and resolves a value using a clear precedence: the script's explicit
     * return value when non-null, otherwise a mutated {@code value} binding if it changed, and
     * finally the original {@code currentValue} when no mutation occurred.
     */
    Object evaluate(CanonicalFieldTransformation transformation, Object currentValue, Map<String, Object> rawRecord) {
        String scriptBody = transformation.getExpression();
        if (scriptBody == null || scriptBody.isBlank()) {
            return currentValue;
        }
        try {
            Script script = instantiateScript(scriptBody);
            Binding binding = new Binding();
            binding.setVariable("value", currentValue);
            binding.setVariable("row", rawRecord);
            binding.setVariable("raw", rawRecord);
            script.setBinding(binding);
            Object result = script.run();
            if (result != null) {
                return result;
            }
            if (binding.hasVariable("value")) {
                Object mutated = binding.getVariable("value");
                if (!Objects.equals(mutated, currentValue)) {
                    return mutated;
                }
            }
            return currentValue;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            throw new TransformationEvaluationException("Failed to execute Groovy transformation", ex);
        } catch (TransformationEvaluationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TransformationEvaluationException("Groovy transformation failed: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> evaluateDataset(String expression, List<Map<String, Object>> rows) {
        if (!StringUtils.hasText(expression)) {
            return rows;
        }
        try {
            List<Map<String, Object>> working = rows == null ? List.of() : rows;
            Script script = instantiateScript(expression);
            Binding binding = new Binding();
            binding.setVariable("rows", working);
            binding.setVariable("records", working);
            binding.setVariable("data", working);
            script.setBinding(binding);
            Object result = script.run();
            if (result instanceof List<?> listResult) {
                return coerceRows(listResult);
            }
            Object mutated = binding.hasVariable("rows") ? binding.getVariable("rows") : null;
            if (mutated instanceof List<?> mutatedList) {
                return coerceRows(mutatedList);
            }
            return working;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            throw new TransformationEvaluationException("Failed to execute Groovy transformation", ex);
        } catch (TransformationEvaluationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TransformationEvaluationException("Groovy transformation failed: " + ex.getMessage(), ex);
        }
    }

    void validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new TransformationEvaluationException("Groovy expression cannot be empty");
        }
        compiledScripts.computeIfAbsent(expression, this::compileScript);
    }

    void validateDatasetScript(String expression) {
        if (!StringUtils.hasText(expression)) {
            return;
        }
        compiledScripts.computeIfAbsent(expression, this::compileScript);
    }

    private Class<? extends Script> compileScript(String body) {
        try {
            Class<?> scriptClass = groovyClassLoader.parseClass(body);
            if (!Script.class.isAssignableFrom(scriptClass)) {
                throw new TransformationEvaluationException("Groovy script must extend Script");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Script> typed = (Class<? extends Script>) scriptClass;
            return typed;
        } catch (TransformationEvaluationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TransformationEvaluationException("Groovy compilation error: " + ex.getMessage(), ex);
        }
    }

    private Script instantiateScript(String scriptBody)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<? extends Script> scriptClass = compiledScripts.computeIfAbsent(scriptBody, this::compileScript);
        return scriptClass.getDeclaredConstructor().newInstance();
    }

    private List<Map<String, Object>> coerceRows(List<?> rawRows) {
        List<Map<String, Object>> coerced = new ArrayList<>();
        for (Object candidate : rawRows) {
            if (candidate == null) {
                continue;
            }
            if (!(candidate instanceof Map<?, ?> map)) {
                throw new TransformationEvaluationException(
                        "Groovy dataset script must return a list of maps (found " + candidate.getClass() + ")");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key == null) {
                    continue;
                }
                row.put(String.valueOf(key), entry.getValue());
            }
            coerced.add(row);
        }
        return coerced;
    }

    private SecureASTCustomizer buildSecurityCustomizer() {
        SecureASTCustomizer customizer = new SecureASTCustomizer();
        customizer.setClosuresAllowed(true);
        customizer.setMethodDefinitionAllowed(false);
        customizer.setPackageAllowed(false);
        customizer.setImportsWhitelist(java.util.Collections.emptyList());
        customizer.setStarImportsWhitelist(java.util.Collections.emptyList());
        customizer.setStaticImportsWhitelist(java.util.Collections.emptyList());
        customizer.setStaticStarImportsWhitelist(java.util.Collections.emptyList());
        customizer.setReceiversClassesWhiteList(java.util.List.of(
                Object.class,
                String.class,
                Number.class,
                Map.class));
        return customizer;
    }
}
