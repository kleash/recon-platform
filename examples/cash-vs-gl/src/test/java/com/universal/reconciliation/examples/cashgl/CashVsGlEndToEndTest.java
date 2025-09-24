package com.universal.reconciliation.examples.cashgl;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
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
    private ReconciliationSourceRepository sourceRepository;

    @Autowired
    private SourceDataBatchRepository batchRepository;

    @Autowired
    private SourceDataRecordRepository recordRepository;

    @Autowired
    private ExportService exportService;

    @Test
    void endToEndFlowLoadsDataExecutesRunAndGeneratesReport() {
        ReconciliationDefinition definition = definitionRepository
                .findByCode("CASH_VS_GL_SIMPLE")
                .orElseThrow();

        var cashSource = sourceRepository
                .findByDefinitionAndCode(definition, "CASH")
                .orElseThrow();
        var glSource = sourceRepository
                .findByDefinitionAndCode(definition, "GL")
                .orElseThrow();

        var cashBatch = batchRepository
                .findFirstBySourceOrderByIngestedAtDesc(cashSource)
                .orElseThrow();
        var glBatch = batchRepository
                .findFirstBySourceOrderByIngestedAtDesc(glSource)
                .orElseThrow();

        assertThat(batchRepository.findBySourceOrderByIngestedAtDesc(cashSource)).hasSize(1);
        assertThat(batchRepository.findBySourceOrderByIngestedAtDesc(glSource)).hasSize(1);
        assertThat(cashBatch.getRecordCount()).isEqualTo(4);
        assertThat(glBatch.getRecordCount()).isEqualTo(4);
        assertThat(recordRepository.findByBatch(cashBatch)).hasSize(4);
        assertThat(recordRepository.findByBatch(glBatch)).hasSize(4);

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
