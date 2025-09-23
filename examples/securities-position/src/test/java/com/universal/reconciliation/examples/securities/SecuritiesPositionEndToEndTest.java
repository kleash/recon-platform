package com.universal.reconciliation.examples.securities;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import com.universal.reconciliation.service.ExportService;
import com.universal.reconciliation.service.ReconciliationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = SecuritiesPositionExampleApplication.class)
class SecuritiesPositionEndToEndTest {

    @Autowired
    private ReconciliationDefinitionRepository definitionRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private SourceRecordARepository sourceRecordARepository;

    @Autowired
    private SourceRecordBRepository sourceRecordBRepository;

    @Autowired
    private ExportService exportService;

    @Test
    void complexScenarioSupportsMakerCheckerAndToleranceMatching() {
        ReconciliationDefinition definition = definitionRepository
                .findByCode("SEC_POSITION_COMPLEX")
                .orElseThrow();

        assertThat(definition.isMakerCheckerEnabled()).isTrue();

        assertThat(sourceRecordARepository.findByDefinition(definition)).hasSize(4);
        assertThat(sourceRecordBRepository.findByDefinition(definition)).hasSize(4);

        var run = reconciliationService.triggerRun(
                definition.getId(),
                List.of("recon-makers", "recon-checkers"),
                "securities-e2e",
                new TriggerRunRequest(TriggerType.SCHEDULED_CRON, "securities-e2e", "Tolerance batch", "securities-e2e"));

        assertThat(run.summary().matched()).isGreaterThan(0);
        assertThat(run.summary().mismatched()).isGreaterThan(0);
        assertThat(run.summary().missing()).isGreaterThan(0);
        assertThat(run.breaks()).isNotEmpty();

        byte[] workbook = exportService.exportToExcel(run);
        assertThat(workbook).isNotEmpty();
    }
}
