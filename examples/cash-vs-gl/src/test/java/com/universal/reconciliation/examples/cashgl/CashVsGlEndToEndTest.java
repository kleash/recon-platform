package com.universal.reconciliation.examples.cashgl;

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

@SpringBootTest(classes = CashVsGlExampleApplication.class)
class CashVsGlEndToEndTest {

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
    void endToEndFlowLoadsDataExecutesRunAndGeneratesReport() {
        ReconciliationDefinition definition = definitionRepository
                .findByCode("CASH_VS_GL_SIMPLE")
                .orElseThrow();

        assertThat(sourceRecordARepository.findByDefinition(definition)).hasSize(4);
        assertThat(sourceRecordBRepository.findByDefinition(definition)).hasSize(4);

        var result = reconciliationService.triggerRun(
                definition.getId(),
                List.of("recon-makers", "recon-checkers"),
                "cash-gl-e2e",
                new TriggerRunRequest(TriggerType.SCHEDULED_CRON, "cash-gl-e2e", "Example automated run", "cash-gl-e2e"));

        assertThat(result.summary().matched()).isGreaterThan(0);
        assertThat(result.summary().mismatched()).isGreaterThan(0);
        assertThat(result.summary().missing()).isGreaterThan(0);

        byte[] workbook = exportService.exportToExcel(result);
        assertThat(workbook).isNotEmpty();
    }
}
