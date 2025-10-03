package com.universal.reconciliation.service.transform;

import com.universal.reconciliation.domain.transform.ColumnOperationConfig;
import com.universal.reconciliation.domain.transform.RowOperationConfig;
import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Applies and validates {@link SourceTransformationPlan} definitions.
 */
@Component
public class SourceTransformationPlanProcessor {

    private final GroovyTransformationEvaluator groovyEvaluator;
    private final FunctionPipelineTransformationEvaluator pipelineEvaluator;

    public SourceTransformationPlanProcessor(
            GroovyTransformationEvaluator groovyEvaluator,
            FunctionPipelineTransformationEvaluator pipelineEvaluator) {
        this.groovyEvaluator = groovyEvaluator;
        this.pipelineEvaluator = pipelineEvaluator;
    }

    public void validate(SourceTransformationPlan plan) {
        if (plan == null) {
            return;
        }
        if (StringUtils.hasText(plan.getDatasetGroovyScript())) {
            groovyEvaluator.validateDatasetScript(plan.getDatasetGroovyScript());
        }
        if (plan.getRowOperations() != null) {
            for (RowOperationConfig operation : plan.getRowOperations()) {
                validateRowOperation(operation);
            }
        }
        if (plan.getColumnOperations() != null) {
            for (ColumnOperationConfig operation : plan.getColumnOperations()) {
                validateColumnOperation(operation);
            }
        }
    }

    public List<Map<String, Object>> apply(SourceTransformationPlan plan, List<Map<String, Object>> rows) {
        List<Map<String, Object>> working = copyRows(rows);
        if (plan == null) {
            return working;
        }
        Set<String> derivedColumns = collectColumnTargets(plan.getColumnOperations());
        applyColumnOperations(plan.getColumnOperations(), working);
        working = applyRowOperations(plan.getRowOperations(), working, derivedColumns);
        applyColumnOperations(plan.getColumnOperations(), working);
        working = normaliseRows(groovyEvaluator.evaluateDataset(plan.getDatasetGroovyScript(), working));
        return working;
    }

    private void validateRowOperation(RowOperationConfig operation) {
        if (operation == null || operation.getType() == null) {
            throw new TransformationEvaluationException("Row operation type is required");
        }
        switch (operation.getType()) {
            case FILTER -> validateFilter(operation.getFilter());
            case AGGREGATE -> validateAggregate(operation.getAggregate());
            case SPLIT -> validateSplit(operation.getSplit());
            default -> throw new TransformationEvaluationException("Unsupported row operation: " + operation.getType());
        }
    }

    private void validateColumnOperation(ColumnOperationConfig operation) {
        if (operation == null || operation.getType() == null) {
            throw new TransformationEvaluationException("Column operation type is required");
        }
        switch (operation.getType()) {
            case COMBINE -> validateCombine(operation.getCombine());
            case PIPELINE -> validatePipeline(operation.getPipeline());
            case ROUND -> validateRound(operation.getRound());
            default -> throw new TransformationEvaluationException("Unsupported column operation: " + operation.getType());
        }
    }

    private void validateFilter(RowOperationConfig.FilterOperation filter) {
        if (filter == null) {
            throw new TransformationEvaluationException("Filter configuration cannot be empty");
        }
        if (!StringUtils.hasText(filter.getColumn())) {
            throw new TransformationEvaluationException("Filter column is required");
        }
        if (filter.getOperator() == null) {
            throw new TransformationEvaluationException("Filter operator is required");
        }
        if ((filter.getOperator() == RowOperationConfig.ComparisonOperator.IN
                        || filter.getOperator() == RowOperationConfig.ComparisonOperator.NOT_IN)
                && CollectionUtils.isEmpty(filter.getValues())) {
            throw new TransformationEvaluationException("IN/NOT_IN filter requires at least one value");
        }
    }

    private void validateAggregate(RowOperationConfig.AggregateOperation aggregate) {
        if (aggregate == null) {
            throw new TransformationEvaluationException("Aggregate configuration cannot be empty");
        }
        if (CollectionUtils.isEmpty(aggregate.getGroupBy())) {
            throw new TransformationEvaluationException("Aggregate operation requires groupBy columns");
        }
        if (CollectionUtils.isEmpty(aggregate.getAggregations())) {
            throw new TransformationEvaluationException("Aggregate operation requires at least one aggregation");
        }
        for (RowOperationConfig.Aggregation aggregation : aggregate.getAggregations()) {
            if (!StringUtils.hasText(aggregation.getSourceColumn())
                    && !StringUtils.hasText(aggregation.getResultColumn())) {
                throw new TransformationEvaluationException("Aggregation requires source or result column");
            }
            if (aggregation.getFunction() == null) {
                throw new TransformationEvaluationException("Aggregation function is required");
            }
            if (aggregation.getScale() != null && aggregation.getScale() < 0) {
                throw new TransformationEvaluationException("Aggregation scale cannot be negative");
            }
        }
    }

    private void validateSplit(RowOperationConfig.SplitOperation split) {
        if (split == null) {
            throw new TransformationEvaluationException("Split configuration cannot be empty");
        }
        if (!StringUtils.hasText(split.getSourceColumn())) {
            throw new TransformationEvaluationException("Split operation requires a source column");
        }
        if (!StringUtils.hasText(split.getDelimiter())) {
            throw new TransformationEvaluationException("Split operation requires a delimiter");
        }
    }

    private void validateCombine(ColumnOperationConfig.CombineOperation combine) {
        if (combine == null) {
            throw new TransformationEvaluationException("Combine configuration cannot be empty");
        }
        if (!StringUtils.hasText(combine.getTargetColumn())) {
            throw new TransformationEvaluationException("Combine operation requires a target column");
        }
        if (CollectionUtils.isEmpty(combine.getSources())) {
            throw new TransformationEvaluationException("Combine operation requires source columns");
        }
    }

    private void validatePipeline(ColumnOperationConfig.PipelineOperation pipeline) {
        if (pipeline == null) {
            throw new TransformationEvaluationException("Pipeline configuration cannot be empty");
        }
        if (!StringUtils.hasText(pipeline.getTargetColumn())) {
            throw new TransformationEvaluationException("Pipeline operation requires a target column");
        }
        if (!StringUtils.hasText(pipeline.getConfiguration())) {
            throw new TransformationEvaluationException("Pipeline configuration JSON is required");
        }
        pipelineEvaluator.validateConfiguration(pipeline.getConfiguration());
    }

    private void validateRound(ColumnOperationConfig.RoundOperation round) {
        if (round == null) {
            throw new TransformationEvaluationException("Round configuration cannot be empty");
        }
        if (!StringUtils.hasText(round.getTargetColumn())) {
            throw new TransformationEvaluationException("Round operation requires a target column");
        }
        if (round.getScale() != null && round.getScale() < 0) {
            throw new TransformationEvaluationException("Round operation scale cannot be negative");
        }
    }

    private List<Map<String, Object>> applyRowOperations(
            List<RowOperationConfig> operations, List<Map<String, Object>> rows, Set<String> derivedColumns) {
        List<Map<String, Object>> working = rows;
        if (operations == null) {
            return working;
        }
        for (RowOperationConfig operation : operations) {
            if (operation == null || operation.getType() == null) {
                continue;
            }
            working = switch (operation.getType()) {
                case FILTER -> applyFilter(operation.getFilter(), working);
                case AGGREGATE -> applyAggregate(operation.getAggregate(), working, derivedColumns);
                case SPLIT -> applySplit(operation.getSplit(), working);
            };
        }
        return working;
    }

    private void applyColumnOperations(List<ColumnOperationConfig> operations, List<Map<String, Object>> rows) {
        if (operations == null) {
            return;
        }
        for (ColumnOperationConfig operation : operations) {
            if (operation == null || operation.getType() == null) {
                continue;
            }
            switch (operation.getType()) {
                case COMBINE -> applyCombine(operation.getCombine(), rows);
                case PIPELINE -> applyPipeline(operation.getPipeline(), rows);
                case ROUND -> applyRound(operation.getRound(), rows);
            }
        }
    }

    private Set<String> collectColumnTargets(List<ColumnOperationConfig> operations) {
        Set<String> targets = new LinkedHashSet<>();
        if (operations == null) {
            return targets;
        }
        for (ColumnOperationConfig operation : operations) {
            if (operation == null || operation.getType() == null) {
                continue;
            }
            switch (operation.getType()) {
                case COMBINE -> {
                    ColumnOperationConfig.CombineOperation combine = operation.getCombine();
                    if (combine != null && StringUtils.hasText(combine.getTargetColumn())) {
                        targets.add(combine.getTargetColumn());
                    }
                }
                case PIPELINE -> {
                    ColumnOperationConfig.PipelineOperation pipeline = operation.getPipeline();
                    if (pipeline != null && StringUtils.hasText(pipeline.getTargetColumn())) {
                        targets.add(pipeline.getTargetColumn());
                    }
                }
                case ROUND -> {
                    ColumnOperationConfig.RoundOperation round = operation.getRound();
                    if (round != null && StringUtils.hasText(round.getTargetColumn())) {
                        targets.add(round.getTargetColumn());
                    }
                }
            }
        }
        return targets;
    }

    private List<Map<String, Object>> applyFilter(
            RowOperationConfig.FilterOperation filter, List<Map<String, Object>> rows) {
        if (filter == null || !StringUtils.hasText(filter.getColumn()) || filter.getOperator() == null) {
            return rows;
        }
        RowOperationConfig.FilterMode mode = Optional.ofNullable(filter.getMode())
                .orElse(RowOperationConfig.FilterMode.RETAIN_MATCHING);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(filter.getColumn());
            boolean matches = evaluateFilter(value, filter);
            boolean retain = mode == RowOperationConfig.FilterMode.RETAIN_MATCHING ? matches : !matches;
            if (retain) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private boolean evaluateFilter(Object value, RowOperationConfig.FilterOperation filter) {
        RowOperationConfig.ComparisonOperator operator = filter.getOperator();
        return switch (operator) {
            case EQUALS -> compareEquality(value, filter.getValue(), filter.isCaseInsensitive());
            case NOT_EQUALS -> !compareEquality(value, filter.getValue(), filter.isCaseInsensitive());
            case IN -> compareInclusion(value, filter.getValues(), filter.isCaseInsensitive());
            case NOT_IN -> !compareInclusion(value, filter.getValues(), filter.isCaseInsensitive());
            case GREATER_THAN -> compareNumeric(value, filter.getValue()) > 0;
            case GREATER_OR_EQUAL -> compareNumeric(value, filter.getValue()) >= 0;
            case LESS_THAN -> compareNumeric(value, filter.getValue()) < 0;
            case LESS_OR_EQUAL -> compareNumeric(value, filter.getValue()) <= 0;
            case IS_BLANK -> isBlank(value);
            case IS_NOT_BLANK -> !isBlank(value);
        };
    }

    private boolean compareEquality(Object left, String right, boolean caseInsensitive) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (caseInsensitive) {
            return left.toString().trim().equalsIgnoreCase(right.trim());
        }
        return Objects.equals(left.toString().trim(), right.trim());
    }

    private boolean compareInclusion(Object value, List<String> values, boolean caseInsensitive) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String candidate : values) {
            if (compareEquality(value, candidate, caseInsensitive)) {
                return true;
            }
        }
        return false;
    }

    private int compareNumeric(Object left, String right) {
        BigDecimal leftNumber = toBigDecimal(left);
        BigDecimal rightNumber = toBigDecimal(right);
        if (leftNumber == null || rightNumber == null) {
            return 0;
        }
        return leftNumber.compareTo(rightNumber);
    }

    private List<Map<String, Object>> applyAggregate(
            RowOperationConfig.AggregateOperation aggregate,
            List<Map<String, Object>> rows,
            Set<String> derivedColumns) {
        if (aggregate == null
                || CollectionUtils.isEmpty(aggregate.getGroupBy())
                || CollectionUtils.isEmpty(aggregate.getAggregations())) {
            return rows;
        }
        Map<List<Object>, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = aggregate.getGroupBy().stream().map(row::get).collect(Collectors.toList());
            grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<List<Object>, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> groupRows = entry.getValue();
            if (groupRows.isEmpty()) {
                continue;
            }
            Map<String, Object> prototype = new LinkedHashMap<>(groupRows.get(0));
            Set<String> retainColumns = new LinkedHashSet<>();
            if (!CollectionUtils.isEmpty(aggregate.getRetainColumns())) {
                retainColumns.addAll(aggregate.getRetainColumns());
            }
            if (derivedColumns != null) {
                retainColumns.addAll(derivedColumns);
            }
            retainColumns.addAll(aggregate.getGroupBy());
            Map<String, Object> row = retainColumns.isEmpty() ? prototype : projectColumns(prototype, retainColumns);
            for (RowOperationConfig.Aggregation aggregation : aggregate.getAggregations()) {
                String sourceColumn = StringUtils.hasText(aggregation.getSourceColumn())
                        ? aggregation.getSourceColumn()
                        : aggregation.getResultColumn();
                String resultColumn = StringUtils.hasText(aggregation.getResultColumn())
                        ? aggregation.getResultColumn()
                        : sourceColumn;
                Object aggregated = aggregateValues(groupRows, sourceColumn, aggregation);
                row.put(resultColumn, aggregated);
            }
            result.add(row);
        }
        if (aggregate.isSortByGroup()) {
            result.sort((a, b) -> compareGroupKeys(a, b, aggregate.getGroupBy()));
        }
        return result;
    }

    private Map<String, Object> projectColumns(Map<String, Object> prototype, Set<String> retainColumns) {
        Map<String, Object> projected = new LinkedHashMap<>();
        for (String column : retainColumns) {
            if (prototype.containsKey(column)) {
                projected.put(column, prototype.get(column));
            }
        }
        return projected;
    }

    private int compareGroupKeys(Map<String, Object> a, Map<String, Object> b, List<String> groupBy) {
        for (String column : groupBy) {
            Object left = a.get(column);
            Object right = b.get(column);
            int comparison = String.valueOf(left).compareTo(String.valueOf(right));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private Object aggregateValues(
            List<Map<String, Object>> rows,
            String column,
            RowOperationConfig.Aggregation aggregation) {
        RowOperationConfig.AggregationFunction function = aggregation.getFunction();
        return switch (function) {
            case SUM -> aggregateSum(rows, column, aggregation);
            case AVG -> aggregateAverage(rows, column, aggregation);
            case MIN -> aggregateMin(rows, column);
            case MAX -> aggregateMax(rows, column);
            case COUNT -> aggregateCount(rows, column);
            case FIRST -> aggregateFirst(rows, column);
            case LAST -> aggregateLast(rows, column);
        };
    }

    private Object aggregateSum(
            List<Map<String, Object>> rows, String column, RowOperationConfig.Aggregation aggregation) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal value = toBigDecimal(row.get(column));
            if (value != null) {
                total = total.add(value);
            }
        }
        return applyScale(total, aggregation.getScale(), aggregation.getRoundingMode());
    }

    private Object aggregateAverage(
            List<Map<String, Object>> rows, String column, RowOperationConfig.Aggregation aggregation) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (Map<String, Object> row : rows) {
            BigDecimal value = toBigDecimal(row.get(column));
            if (value != null) {
                total = total.add(value);
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(count), MathContext.DECIMAL64);
        return applyScale(average, aggregation.getScale(), aggregation.getRoundingMode());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object aggregateMin(List<Map<String, Object>> rows, String column) {
        Comparable min = null;
        for (Map<String, Object> row : rows) {
            Object value = row.get(column);
            if (value instanceof Comparable comparable) {
                if (min == null || comparable.compareTo(min) < 0) {
                    min = comparable;
                }
            }
        }
        return min;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object aggregateMax(List<Map<String, Object>> rows, String column) {
        Comparable max = null;
        for (Map<String, Object> row : rows) {
            Object value = row.get(column);
            if (value instanceof Comparable comparable) {
                if (max == null || comparable.compareTo(max) > 0) {
                    max = comparable;
                }
            }
        }
        return max;
    }

    private Object aggregateCount(List<Map<String, Object>> rows, String column) {
        long count = rows.stream().filter(row -> row.get(column) != null).count();
        return count;
    }

    private Object aggregateFirst(List<Map<String, Object>> rows, String column) {
        for (Map<String, Object> row : rows) {
            Object value = row.get(column);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object aggregateLast(List<Map<String, Object>> rows, String column) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            Object value = rows.get(i).get(column);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<Map<String, Object>> applySplit(
            RowOperationConfig.SplitOperation split, List<Map<String, Object>> rows) {
        if (split == null || !StringUtils.hasText(split.getSourceColumn())) {
            return rows;
        }
        String delimiter = StringUtils.hasText(split.getDelimiter()) ? split.getDelimiter() : ",";
        String targetColumn = StringUtils.hasText(split.getTargetColumn())
                ? split.getTargetColumn()
                : split.getSourceColumn();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get(split.getSourceColumn());
            if (!(raw instanceof String rawString)) {
                result.add(row);
                continue;
            }
            if (!StringUtils.hasText(rawString)) {
                if (!split.isDropEmptyValues()) {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    copy.put(targetColumn, rawString);
                    result.add(copy);
                }
                continue;
            }
            String[] tokens = rawString.split(java.util.regex.Pattern.quote(delimiter));
            boolean emitted = false;
            for (String token : tokens) {
                String value = split.isTrimValues() ? token.trim() : token;
                if (split.isDropEmptyValues() && !StringUtils.hasText(value)) {
                    continue;
                }
                Map<String, Object> copy = new LinkedHashMap<>(row);
                copy.put(targetColumn, value);
                result.add(copy);
                emitted = true;
            }
            if (!emitted && !split.isDropEmptyValues()) {
                Map<String, Object> copy = new LinkedHashMap<>(row);
                copy.put(targetColumn, split.isTrimValues() ? rawString.trim() : rawString);
                result.add(copy);
            }
        }
        return result;
    }

    private void applyCombine(ColumnOperationConfig.CombineOperation combine, List<Map<String, Object>> rows) {
        if (combine == null || !StringUtils.hasText(combine.getTargetColumn())) {
            return;
        }
        String delimiter = combine.getDelimiter() != null ? combine.getDelimiter() : "";
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String source : combine.getSources()) {
                Object raw = row.get(source);
                if (raw == null) {
                    continue;
                }
                String text = raw.toString();
                if (combine.isSkipBlanks() && !StringUtils.hasText(text)) {
                    continue;
                }
                values.add(text);
            }
            String joined = String.join(delimiter, values);
            if (StringUtils.hasText(combine.getPrefix())) {
                joined = combine.getPrefix() + joined;
            }
            if (StringUtils.hasText(combine.getSuffix())) {
                joined = joined + combine.getSuffix();
            }
            row.put(combine.getTargetColumn(), joined);
        }
    }

    private void applyPipeline(ColumnOperationConfig.PipelineOperation pipeline, List<Map<String, Object>> rows) {
        if (pipeline == null || !StringUtils.hasText(pipeline.getTargetColumn())) {
            return;
        }
        String configuration = pipeline.getConfiguration();
        if (!StringUtils.hasText(configuration)) {
            return;
        }
        for (Map<String, Object> row : rows) {
            String sourceColumn = StringUtils.hasText(pipeline.getSourceColumn())
                    ? pipeline.getSourceColumn()
                    : pipeline.getTargetColumn();
            Object currentValue = row.get(sourceColumn);
            Object evaluated = pipelineEvaluator.evaluateConfiguration(configuration, currentValue, row);
            row.put(pipeline.getTargetColumn(), evaluated);
        }
    }

    private void applyRound(ColumnOperationConfig.RoundOperation round, List<Map<String, Object>> rows) {
        if (round == null || !StringUtils.hasText(round.getTargetColumn())) {
            return;
        }
        int scale = Optional.ofNullable(round.getScale()).orElse(2);
        RoundingMode mode = Optional.ofNullable(round.getRoundingMode()).orElse(RoundingMode.HALF_UP);
        for (Map<String, Object> row : rows) {
            String sourceColumn = StringUtils.hasText(round.getSourceColumn())
                    ? round.getSourceColumn()
                    : round.getTargetColumn();
            Object raw = row.get(sourceColumn);
            BigDecimal number = toBigDecimal(raw);
            if (number == null) {
                continue;
            }
            row.put(round.getTargetColumn(), number.setScale(scale, mode));
        }
    }

    private List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (rows == null) {
            return copy;
        }
        for (Map<String, Object> row : rows) {
            copy.add(new LinkedHashMap<>(row));
        }
        return copy;
    }

    private List<Map<String, Object>> normaliseRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> normalised = new ArrayList<>();
        if (rows == null) {
            return normalised;
        }
        for (Map<String, Object> row : rows) {
            if (row instanceof LinkedHashMap<String, Object> linked) {
                normalised.add(linked);
            } else {
                normalised.add(new LinkedHashMap<>(row));
            }
        }
        return normalised;
    }

    private boolean isBlank(Object value) {
        return value == null || !StringUtils.hasText(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = value.toString().trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Object applyScale(BigDecimal value, Integer scale, RoundingMode mode) {
        if (value == null) {
            return null;
        }
        if (scale == null) {
            return value;
        }
        return value.setScale(scale, Optional.ofNullable(mode).orElse(RoundingMode.HALF_UP));
    }
}
