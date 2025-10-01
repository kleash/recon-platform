package com.universal.reconciliation.domain.transform;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes the per-source transformation pipeline that admins can configure
 * before canonical mappings are applied.
 */
@Data
@NoArgsConstructor
public class SourceTransformationPlan {

    /** Groovy script that can reshape the entire dataset before row/column operations run. */
    private String datasetGroovyScript;

    /** Ordered list of row-level operations (filtering, aggregation, splitting, etc.). */
    private List<RowOperationConfig> rowOperations = new ArrayList<>();

    /** Ordered list of column-level operations (derivations, rounding, pipelines, etc.). */
    private List<ColumnOperationConfig> columnOperations = new ArrayList<>();
}
