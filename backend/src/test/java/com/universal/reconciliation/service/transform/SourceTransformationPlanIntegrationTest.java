package com.universal.reconciliation.service.transform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationPreviewUploadRequest;
import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import com.universal.reconciliation.domain.transform.ColumnOperationConfig;
import com.universal.reconciliation.domain.transform.RowOperationConfig;
import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class SourceTransformationPlanIntegrationTest {

    private final TransformationSampleFileService sampleFileService =
            new TransformationSampleFileService(new ObjectMapper(), 2L * 1024 * 1024);

    private final SourceTransformationPlanProcessor processor = new SourceTransformationPlanProcessor(
            new GroovyTransformationEvaluator(),
            new FunctionPipelineTransformationEvaluator(new ObjectMapper()));

    @Test
    void endToEndCsvPreviewAndPlanApplication() {
        String csv = "tradeId,curve,tenor,amount\n"
                + "T1,USD,1Y,100.125\n"
                + "T1,USD,1Y,50.377\n"
                + "T1,USD,3M,42.000\n"
                + "T2,EUR,2Y,200.456\n"
                + "T2,EUR,5Y,125.111\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "trades.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.CSV,
                true,
                ",",
                null,
                null,
                null,
                10,
                null);

        List<Map<String, Object>> rawRows = sampleFileService.parseSamples(request, file);
        assertThat(rawRows).hasSize(5);

        SourceTransformationPlan plan = buildPlan();
        List<Map<String, Object>> transformed = processor.apply(plan, rawRows);

        assertThat(transformed)
                .hasSize(2)
                .anySatisfy(row -> {
                    if ("T1|USD|1Y".equals(row.get("matchingKey"))) {
                        assertThat(row.get("tradeId")).isEqualTo("T1");
                        assertThat(row.get("curve")).isEqualTo("USD");
                        assertThat(row.get("tenor")).isEqualTo("1Y");
                        assertThat(row.get("amount").toString()).isEqualTo("150.50");
                    }
                })
                .anySatisfy(row -> {
                    if ("T2|EUR|2Y".equals(row.get("matchingKey"))) {
                        assertThat(row.get("tradeId")).isEqualTo("T2");
                        assertThat(row.get("curve")).isEqualTo("EUR");
                        assertThat(row.get("tenor")).isEqualTo("2Y");
                        assertThat(row.get("amount").toString()).isEqualTo("200.46");
                    }
                });
    }

    private SourceTransformationPlan buildPlan() {
        SourceTransformationPlan plan = new SourceTransformationPlan();

        RowOperationConfig excludeThreeMonth = new RowOperationConfig();
        RowOperationConfig.FilterOperation excludeFilter = new RowOperationConfig.FilterOperation();
        excludeFilter.setColumn("tenor");
        excludeFilter.setOperator(RowOperationConfig.ComparisonOperator.EQUALS);
        excludeFilter.setValue("3M");
        excludeFilter.setMode(RowOperationConfig.FilterMode.EXCLUDE_MATCHING);
        excludeThreeMonth.setType(RowOperationConfig.RowOperationType.FILTER);
        excludeThreeMonth.setFilter(excludeFilter);

        RowOperationConfig retainYearBuckets = new RowOperationConfig();
        RowOperationConfig.FilterOperation retainFilter = new RowOperationConfig.FilterOperation();
        retainFilter.setColumn("tenor");
        retainFilter.setOperator(RowOperationConfig.ComparisonOperator.IN);
        retainFilter.setValues(List.of("1Y", "2Y", "3Y"));
        retainYearBuckets.setType(RowOperationConfig.RowOperationType.FILTER);
        retainYearBuckets.setFilter(retainFilter);

        RowOperationConfig aggregate = new RowOperationConfig();
        RowOperationConfig.AggregateOperation aggregateConfig = new RowOperationConfig.AggregateOperation();
        aggregateConfig.getGroupBy().addAll(List.of("tradeId", "curve", "tenor"));
        RowOperationConfig.Aggregation amountSum = new RowOperationConfig.Aggregation();
        amountSum.setSourceColumn("amount");
        amountSum.setResultColumn("amount");
        amountSum.setFunction(RowOperationConfig.AggregationFunction.SUM);
        aggregateConfig.getAggregations().add(amountSum);
        aggregate.setType(RowOperationConfig.RowOperationType.AGGREGATE);
        aggregate.setAggregate(aggregateConfig);

        plan.getRowOperations().addAll(List.of(excludeThreeMonth, retainYearBuckets, aggregate));

        ColumnOperationConfig createKey = new ColumnOperationConfig();
        ColumnOperationConfig.CombineOperation combineOperation = new ColumnOperationConfig.CombineOperation();
        combineOperation.setTargetColumn("matchingKey");
        combineOperation.getSources().addAll(List.of("tradeId", "curve", "tenor"));
        combineOperation.setDelimiter("|");
        createKey.setType(ColumnOperationConfig.ColumnOperationType.COMBINE);
        createKey.setCombine(combineOperation);

        ColumnOperationConfig roundAmount = new ColumnOperationConfig();
        ColumnOperationConfig.RoundOperation roundOperation = new ColumnOperationConfig.RoundOperation();
        roundOperation.setTargetColumn("amount");
        roundOperation.setScale(2);
        roundAmount.setType(ColumnOperationConfig.ColumnOperationType.ROUND);
        roundAmount.setRound(roundOperation);

        plan.getColumnOperations().addAll(List.of(createKey, roundAmount));
        return plan;
    }
}
