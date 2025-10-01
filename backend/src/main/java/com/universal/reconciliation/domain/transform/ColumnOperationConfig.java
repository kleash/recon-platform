package com.universal.reconciliation.domain.transform;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration container for a single column-level transformation.
 */
@Data
@NoArgsConstructor
public class ColumnOperationConfig {

    private ColumnOperationType type;
    private CombineOperation combine;
    private PipelineOperation pipeline;
    private RoundOperation round;

    public enum ColumnOperationType {
        COMBINE,
        PIPELINE,
        ROUND
    }

    @Data
    @NoArgsConstructor
    public static class CombineOperation {
        private String targetColumn;
        private List<String> sources = new ArrayList<>();
        private String delimiter = "|";
        private boolean skipBlanks = true;
        private String prefix;
        private String suffix;
    }

    @Data
    @NoArgsConstructor
    public static class PipelineOperation {
        private String targetColumn;
        private String sourceColumn;
        /** Serialized configuration matching the function pipeline evaluator contract. */
        private String configuration;
    }

    @Data
    @NoArgsConstructor
    public static class RoundOperation {
        private String targetColumn;
        private String sourceColumn;
        private Integer scale = 2;
        private RoundingMode roundingMode = RoundingMode.HALF_UP;
    }
}
