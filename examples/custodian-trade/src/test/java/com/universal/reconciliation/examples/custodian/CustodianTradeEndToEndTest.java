package com.universal.reconciliation.examples.custodian;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = CustodianTradeExampleApplication.class)
class CustodianTradeEndToEndTest {

    @Autowired
    private ReconciliationDefinitionRepository definitionRepository;

    @Autowired
    private SourceRecordARepository sourceRecordARepository;

    @Autowired
    private SourceRecordBRepository sourceRecordBRepository;

    @Autowired
    private CustodianTradeScheduler scheduler;

    @Test
    void scenarioCoversAutoAndCutoffTriggersWithScheduledReports() {
        ReconciliationDefinition definition = definitionRepository
                .findByCode("CUSTODIAN_TRADE_COMPLEX")
                .orElseThrow();

        assertThat(sourceRecordARepository.findByDefinition(definition)).hasSize(8);
        assertThat(sourceRecordBRepository.findByDefinition(definition)).hasSize(8);

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
