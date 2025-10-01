package com.universal.reconciliation.domain.transform;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration container for a single row-level transformation step.
 */
@Data
@NoArgsConstructor
public class RowOperationConfig {

    private RowOperationType type;
    private FilterOperation filter;
    private AggregateOperation aggregate;
    private SplitOperation split;

    public enum RowOperationType {
        FILTER,
        AGGREGATE,
        SPLIT
    }

    @Data
    @NoArgsConstructor
    public static class FilterOperation {
        private FilterMode mode = FilterMode.RETAIN_MATCHING;
        private ComparisonOperator operator = ComparisonOperator.EQUALS;
        private String column;
        private String value;
        private List<String> values = new ArrayList<>();
        private boolean caseInsensitive;
    }

    public enum FilterMode {
        /** Keep rows that satisfy the predicate. */
        RETAIN_MATCHING,
        /** Drop rows that satisfy the predicate. */
        EXCLUDE_MATCHING
    }

    public enum ComparisonOperator {
        EQUALS,
        NOT_EQUALS,
        IN,
        NOT_IN,
        GREATER_THAN,
        GREATER_OR_EQUAL,
        LESS_THAN,
        LESS_OR_EQUAL,
        IS_BLANK,
        IS_NOT_BLANK
    }

    @Data
    @NoArgsConstructor
    public static class AggregateOperation {
        private List<String> groupBy = new ArrayList<>();
        private List<Aggregation> aggregations = new ArrayList<>();
        private Set<String> retainColumns = new HashSet<>();
        private boolean sortByGroup;
    }

    @Data
    @NoArgsConstructor
    public static class Aggregation {
        private String sourceColumn;
        private String resultColumn;
        private AggregationFunction function = AggregationFunction.SUM;
        private Integer scale;
        private RoundingMode roundingMode = RoundingMode.HALF_UP;
    }

    public enum AggregationFunction {
        SUM,
        AVG,
        MIN,
        MAX,
        COUNT,
        FIRST,
        LAST
    }

    @Data
    @NoArgsConstructor
    public static class SplitOperation {
        private String sourceColumn;
        private String targetColumn;
        private String delimiter = ",";
        private boolean trimValues = true;
        private boolean dropEmptyValues = true;
    }
}
