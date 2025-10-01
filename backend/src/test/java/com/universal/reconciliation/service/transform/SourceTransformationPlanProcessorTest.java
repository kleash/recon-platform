package com.universal.reconciliation.service.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.transform.ColumnOperationConfig;
import com.universal.reconciliation.domain.transform.RowOperationConfig;
import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SourceTransformationPlanProcessorTest {

    private SourceTransformationPlanProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SourceTransformationPlanProcessor(
                new GroovyTransformationEvaluator(),
                new FunctionPipelineTransformationEvaluator(new ObjectMapper()));
    }

    @Test
    void applyRemovesRowsExcludedByFilter() {
        SourceTransformationPlan plan = new SourceTransformationPlan();
        RowOperationConfig config = new RowOperationConfig();
        RowOperationConfig.FilterOperation filter = new RowOperationConfig.FilterOperation();
        filter.setColumn("tenor");
        filter.setOperator(RowOperationConfig.ComparisonOperator.EQUALS);
        filter.setValue("3M");
        filter.setMode(RowOperationConfig.FilterMode.EXCLUDE_MATCHING);
        config.setType(RowOperationConfig.RowOperationType.FILTER);
        config.setFilter(filter);
        plan.getRowOperations().add(config);

        List<Map<String, Object>> rows = List.of(
                Map.of("tenor", "3M", "amount", 100),
                Map.of("tenor", "1Y", "amount", 200));

        List<Map<String, Object>> result = processor.apply(plan, rows);

        assertThat(result)
                .hasSize(1)
                .first()
                .satisfies(row -> {
                    assertThat(row.get("tenor")).isEqualTo("1Y");
                    assertThat(row.get("amount")).isEqualTo(200);
                });
    }

    @Test
    void applyAggregatesRowsByGroup() {
        SourceTransformationPlan plan = new SourceTransformationPlan();
        RowOperationConfig aggregateConfig = new RowOperationConfig();
        RowOperationConfig.AggregateOperation aggregate = new RowOperationConfig.AggregateOperation();
        aggregate.getGroupBy().add("tradeId");
        RowOperationConfig.Aggregation amountSum = new RowOperationConfig.Aggregation();
        amountSum.setSourceColumn("amount");
        amountSum.setResultColumn("amount");
        amountSum.setFunction(RowOperationConfig.AggregationFunction.SUM);
        amountSum.setScale(2);
        aggregate.getAggregations().add(amountSum);
        aggregate.getRetainColumns().add("tradeId");
        aggregateConfig.setType(RowOperationConfig.RowOperationType.AGGREGATE);
        aggregateConfig.setAggregate(aggregate);
        plan.getRowOperations().add(aggregateConfig);

        List<Map<String, Object>> rows = List.of(
                Map.of("tradeId", "T1", "amount", "100.123"),
                Map.of("tradeId", "T1", "amount", "50.111"),
                Map.of("tradeId", "T2", "amount", "75"));

        List<Map<String, Object>> result = processor.apply(plan, rows);

        assertThat(result)
                .hasSize(2)
                .anySatisfy(row -> {
                    if ("T1".equals(row.get("tradeId"))) {
                        assertThat(row.get("amount")).isEqualTo(new java.math.BigDecimal("150.23"));
                    }
                });
    }

    @Test
    void applySplitAndCombineCreatesDerivedColumn() {
        SourceTransformationPlan plan = new SourceTransformationPlan();

        RowOperationConfig splitConfig = new RowOperationConfig();
        RowOperationConfig.SplitOperation split = new RowOperationConfig.SplitOperation();
        split.setSourceColumn("tenor");
        split.setDelimiter("|");
        split.setTrimValues(true);
        split.setDropEmptyValues(true);
        splitConfig.setType(RowOperationConfig.RowOperationType.SPLIT);
        splitConfig.setSplit(split);
        plan.getRowOperations().add(splitConfig);

        ColumnOperationConfig combineConfig = new ColumnOperationConfig();
        ColumnOperationConfig.CombineOperation combine = new ColumnOperationConfig.CombineOperation();
        combine.setTargetColumn("matchingKey");
        combine.getSources().addAll(List.of("tradeId", "curve", "tenor"));
        combine.setDelimiter(":");
        combineConfig.setType(ColumnOperationConfig.ColumnOperationType.COMBINE);
        combineConfig.setCombine(combine);
        plan.getColumnOperations().add(combineConfig);

        List<Map<String, Object>> rows = List.of(
                Map.of("tradeId", "T1", "curve", "USD", "tenor", "1Y|2Y"));

        List<Map<String, Object>> result = processor.apply(plan, rows);

        assertThat(result)
                .hasSize(2)
                .allSatisfy(row -> assertThat(row).containsKey("matchingKey"));
        assertThat(result)
                .extracting(row -> row.get("matchingKey"))
                .containsExactlyInAnyOrder("T1:USD:1Y", "T1:USD:2Y");
    }

    @Test
    void applyRoundOperationAdjustsScale() {
        SourceTransformationPlan plan = new SourceTransformationPlan();
        ColumnOperationConfig roundConfig = new ColumnOperationConfig();
        ColumnOperationConfig.RoundOperation round = new ColumnOperationConfig.RoundOperation();
        round.setTargetColumn("amount");
        round.setScale(2);
        round.setRoundingMode(RoundingMode.HALF_UP);
        roundConfig.setType(ColumnOperationConfig.ColumnOperationType.ROUND);
        roundConfig.setRound(round);
        plan.getColumnOperations().add(roundConfig);

        List<Map<String, Object>> rows = List.of(Map.of("amount", "123.4567"));

        List<Map<String, Object>> result = processor.apply(plan, rows);

        assertThat(result)
                .singleElement()
                .satisfies(row -> assertThat(row.get("amount")).isEqualTo(new java.math.BigDecimal("123.46")));
    }

    @Test
    void validateRejectsSplitWithoutSourceColumn() {
        SourceTransformationPlan plan = new SourceTransformationPlan();
        RowOperationConfig config = new RowOperationConfig();
        config.setType(RowOperationConfig.RowOperationType.SPLIT);
        config.setSplit(new RowOperationConfig.SplitOperation());
        plan.getRowOperations().add(config);

        assertThatThrownBy(() -> processor.validate(plan))
                .isInstanceOf(TransformationEvaluationException.class)
                .hasMessageContaining("source column");
    }

    @Test
    void datasetGroovyScriptCanMutateRows() {
        SourceTransformationPlan plan = new SourceTransformationPlan();
        plan.setDatasetGroovyScript("rows.each { it.amount = (it.amount as BigDecimal) * 2 }");

        List<Map<String, Object>> rows = List.of(Map.of("amount", "5"));

        List<Map<String, Object>> result = processor.apply(plan, rows);

        assertThat(result)
                .singleElement()
                .satisfies(row -> assertThat(row.get("amount")).isEqualTo(new java.math.BigDecimal("10")));
    }
}
