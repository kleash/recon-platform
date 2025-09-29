package com.universal.reconciliation.service.transform;

import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Component;

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

    Object evaluate(CanonicalFieldTransformation transformation, Object currentValue, Map<String, Object> rawRecord) {
        String scriptBody = transformation.getExpression();
        if (scriptBody == null || scriptBody.isBlank()) {
            return currentValue;
        }
        try {
            Class<? extends Script> scriptClass = compiledScripts.computeIfAbsent(scriptBody, this::compileScript);
            Script script = scriptClass.getDeclaredConstructor().newInstance();
            Binding binding = new Binding();
            binding.setVariable("value", currentValue);
            binding.setVariable("row", rawRecord);
            binding.setVariable("raw", rawRecord);
            script.setBinding(binding);
            Object result = script.run();
            if (result != null) {
                return result;
            }
            try {
                Object mutated = binding.getVariable("value");
                if (!Objects.equals(mutated, currentValue)) {
                    return mutated;
                }
            } catch (MissingPropertyException ignored) {
                // Script did not reference the bound value variable; fall back to original input.
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

    void validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new TransformationEvaluationException("Groovy expression cannot be empty");
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
