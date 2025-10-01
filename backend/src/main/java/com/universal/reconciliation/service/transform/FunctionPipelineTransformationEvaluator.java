package com.universal.reconciliation.service.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Hydrates and executes UI-authored transformation function pipelines.
 */
@Component
class FunctionPipelineTransformationEvaluator {

    private final ObjectMapper objectMapper;
    private final Map<String, BiFunction<FunctionCallContext, List<String>, Object>> handlers = new HashMap<>();

    FunctionPipelineTransformationEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        registerDefaultHandlers();
    }

    Object evaluate(CanonicalFieldTransformation transformation, Object currentValue, Map<String, Object> rawRecord) {
        if (!StringUtils.hasText(transformation.getConfiguration())) {
            return currentValue;
        }
        PipelineDefinition definition = readPipeline(transformation.getConfiguration());
        if (definition.steps() == null || definition.steps().isEmpty()) {
            return currentValue;
        }
        Object value = currentValue;
        FunctionCallContext context = new FunctionCallContext(value, rawRecord);
        for (PipelineStep step : definition.steps()) {
            BiFunction<FunctionCallContext, List<String>, Object> handler =
                    handlers.get(step.function().toUpperCase(Locale.ROOT));
            if (handler == null) {
                throw new TransformationEvaluationException("Unsupported function: " + step.function());
            }
            context = new FunctionCallContext(handler.apply(context, step.args()), rawRecord);
        }
        return context.value();
    }

    Object evaluateConfiguration(String configuration, Object currentValue, Map<String, Object> rawRecord) {
        if (!StringUtils.hasText(configuration)) {
            return currentValue;
        }
        PipelineDefinition definition = readPipeline(configuration);
        if (definition.steps() == null || definition.steps().isEmpty()) {
            return currentValue;
        }
        Object value = currentValue;
        FunctionCallContext context = new FunctionCallContext(value, rawRecord == null ? Map.of() : rawRecord);
        for (PipelineStep step : definition.steps()) {
            BiFunction<FunctionCallContext, List<String>, Object> handler =
                    handlers.get(step.function().toUpperCase(Locale.ROOT));
            if (handler == null) {
                throw new TransformationEvaluationException("Unsupported function: " + step.function());
            }
            context = new FunctionCallContext(handler.apply(context, step.args()), context.rawRecord());
        }
        return context.value();
    }

    void validateConfiguration(String configuration) {
        readPipeline(configuration);
    }

    private PipelineDefinition readPipeline(String configuration) {
        try {
            return objectMapper.readValue(configuration, PipelineDefinition.class);
        } catch (Exception ex) {
            throw new TransformationEvaluationException("Invalid function pipeline configuration", ex);
        }
    }

    private void registerDefaultHandlers() {
        handlers.put("TRIM", (ctx, args) -> {
            Object value = ctx.value();
            return value == null ? null : value.toString().trim();
        });
        handlers.put("TO_UPPERCASE", (ctx, args) -> ctx.value() == null ? null : ctx.value().toString().toUpperCase(Locale.ROOT));
        handlers.put("TO_LOWERCASE", (ctx, args) -> ctx.value() == null ? null : ctx.value().toString().toLowerCase(Locale.ROOT));
        handlers.put("REPLACE", (ctx, args) -> {
            if (ctx.value() == null) {
                return null;
            }
            if (args.size() < 2) {
                throw new TransformationEvaluationException("REPLACE requires two arguments");
            }
            String target = resolveArg(args.get(0), ctx.rawRecord());
            String replacement = resolveArg(args.get(1), ctx.rawRecord());
            return ctx.value().toString().replace(target, replacement);
        });
        handlers.put("SUBSTRING", (ctx, args) -> {
            if (ctx.value() == null) {
                return null;
            }
            if (args.isEmpty()) {
                throw new TransformationEvaluationException("SUBSTRING requires at least a start index");
            }
            try {
                int start = Integer.parseInt(args.get(0));
                int end = args.size() > 1 ? Integer.parseInt(args.get(1)) : ctx.value().toString().length();
                String value = ctx.value().toString();
                return value.substring(Math.max(0, start), Math.min(value.length(), end));
            } catch (NumberFormatException ex) {
                throw new TransformationEvaluationException("SUBSTRING arguments must be numeric", ex);
            }
        });
        handlers.put("DEFAULT_IF_BLANK", (ctx, args) -> {
            Object value = ctx.value();
            if (value == null || !StringUtils.hasText(value.toString())) {
                return args.isEmpty() ? null : resolveArg(args.get(0), ctx.rawRecord());
            }
            return value;
        });
        handlers.put("PREFIX", (ctx, args) -> {
            String prefix = args.isEmpty() ? "" : resolveArg(args.get(0), ctx.rawRecord());
            return prefix + Optional.ofNullable(ctx.value()).map(Object::toString).orElse("");
        });
        handlers.put("SUFFIX", (ctx, args) -> {
            String suffix = args.isEmpty() ? "" : resolveArg(args.get(0), ctx.rawRecord());
            return Optional.ofNullable(ctx.value()).map(Object::toString).orElse("") + suffix;
        });
        handlers.put("FORMAT_DATE", (ctx, args) -> {
            if (ctx.value() == null) {
                return null;
            }
            if (args.size() < 2) {
                throw new TransformationEvaluationException("FORMAT_DATE requires source and target patterns");
            }
            String sourcePattern = args.get(0);
            String targetPattern = args.get(1);
            try {
                LocalDate parsed = LocalDate.parse(ctx.value().toString(), DateTimeFormatter.ofPattern(sourcePattern));
                return parsed.format(DateTimeFormatter.ofPattern(targetPattern));
            } catch (DateTimeParseException ex) {
                throw new TransformationEvaluationException("Unable to format date: " + ex.getMessage(), ex);
            }
        });
    }

    private String resolveArg(String rawArg, Map<String, Object> rawRecord) {
        if (rawArg == null) {
            return null;
        }
        if (rawArg.startsWith("{{") && rawArg.endsWith("}}")) {
            String key = rawArg.substring(2, rawArg.length() - 2);
            Object value = rawRecord.get(key);
            return value != null ? value.toString() : null;
        }
        return rawArg;
    }

    private record PipelineDefinition(List<PipelineStep> steps) {}

    private record PipelineStep(String function, List<String> args) {
        private PipelineStep {
            if (function == null || function.isBlank()) {
                throw new TransformationEvaluationException("Function name cannot be empty");
            }
            args = args == null ? List.of() : List.copyOf(args);
        }
    }

    private record FunctionCallContext(Object value, Map<String, Object> rawRecord) {}
}
