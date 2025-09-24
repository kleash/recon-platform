package com.universal.reconciliation.examples.custodian;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = CustodianTradeExampleApplication.class)
class CustodianTradeEndToEndTest {

    @Autowired
    private ReconciliationDefinitionRepository definitionRepository;

    @Autowired
    private ReconciliationSourceRepository sourceRepository;

    @Autowired
    private SourceDataBatchRepository batchRepository;

    @Autowired
    private SourceDataRecordRepository recordRepository;

    @Autowired
    private CustodianTradeScheduler scheduler;

    @Test
    void scenarioCoversAutoAndCutoffTriggersWithScheduledReports() {
        ReconciliationDefinition definition = definitionRepository
                .findByCode("CUSTODIAN_TRADE_COMPLEX")
                .orElseThrow();

        var custodianSource = sourceRepository
                .findByDefinitionAndCode(definition, "CUSTODIAN")
                .orElseThrow();
        var platformSource = sourceRepository
                .findByDefinitionAndCode(definition, "PLATFORM")
                .orElseThrow();

        var custodianBatches = batchRepository.findBySourceOrderByIngestedAtDesc(custodianSource);
        var platformBatches = batchRepository.findBySourceOrderByIngestedAtDesc(platformSource);

        assertThat(custodianBatches).hasSize(6);
        assertThat(platformBatches).hasSize(2);
        assertThat(custodianBatches).allSatisfy(batch -> assertThat(batch.getRecordCount()).isGreaterThan(0));
        assertThat(platformBatches).allSatisfy(batch -> assertThat(batch.getRecordCount()).isGreaterThan(0));

        long custodianRecords = custodianBatches.stream()
                .mapToLong(batch -> batch.getRecordCount() == null ? 0 : batch.getRecordCount())
                .sum();
        long platformRecords = platformBatches.stream()
                .mapToLong(batch -> batch.getRecordCount() == null ? 0 : batch.getRecordCount())
                .sum();

        assertThat(custodianRecords).isEqualTo(8);
        assertThat(platformRecords).isEqualTo(8);
        assertThat(recordRepository.findByBatch(custodianBatches.get(0))).isNotEmpty();
        assertThat(recordRepository.findByBatch(platformBatches.get(0))).isNotEmpty();

        Map<CutoffCycle, CustodianTradeScheduler.CutoffResult> resultByCycle = scheduler.results().stream()
                .collect(java.util.stream.Collectors.toMap(CustodianTradeScheduler.CutoffResult::cycle, r -> r));

        CustodianTradeScheduler.CutoffResult morning = resultByCycle.get(CutoffCycle.MORNING);
        CustodianTradeScheduler.CutoffResult evening = resultByCycle.get(CutoffCycle.EVENING);

        assertThat(morning.triggeredByCutoff()).isFalse();
        assertThat(morning.runDetail().summary().matched()).isGreaterThan(0);
        assertThat(morning.runDetail().summary().triggerComments()).contains("automatically");

        assertThat(evening.triggeredByCutoff()).isTrue();
        assertThat(evening.runDetail().summary().triggerComments()).contains("cutoff");
        assertThat(evening.runDetail().summary().missing()).isGreaterThan(0);

        var reportExecutions = scheduler.reportExecutions();
        assertThat(reportExecutions).hasSize(3);
        assertThat(reportExecutions)
                .allSatisfy(execution -> assertThat(execution.workbook()).isNotEmpty());
    }
}
