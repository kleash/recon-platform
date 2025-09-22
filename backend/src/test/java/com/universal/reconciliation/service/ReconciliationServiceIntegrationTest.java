package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReconciliationServiceIntegrationTest {

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconciliationDefinitionRepository definitionRepository;

    private final List<String> groups = List.of("recon-makers", "recon-checkers");

    @Test
    void fetchLatestRun_returnsFilteredBreaksAndMetadata() {
        Long definitionId = definitionId("CASH_VS_GL_SIMPLE");
        RunDetailDto initial = reconciliationService.triggerRun(
                definitionId,
                groups,
                "integration-test",
                new TriggerRunRequest(TriggerType.MANUAL_API, "it-run", "integration test", null));
        assertThat(initial.summary().runId()).isNotNull();
        assertThat(initial.summary().triggerType()).isEqualTo(TriggerType.MANUAL_API);
        assertThat(initial.analytics().totalBreakCount()).isGreaterThan(0);

        RunDetailDto usBreaks = reconciliationService.fetchLatestRun(
                definitionId,
                groups,
                new BreakFilterCriteria("Payments", "Wire", "US", Set.of(BreakStatus.OPEN)));

        assertThat(usBreaks.breaks())
                .allSatisfy(breakItem -> assertThat(breakItem.entity()).isEqualTo("US"));
        assertThat(usBreaks.filters().products()).contains("Payments");
        assertThat(usBreaks.analytics().breaksByStatus()).containsKey(BreakStatus.OPEN.name());

        RunDetailDto euOnly = reconciliationService.fetchLatestRun(
                definitionId,
                groups,
                new BreakFilterCriteria(null, null, "EU", Set.of(BreakStatus.OPEN)));

        assertThat(euOnly.breaks())
                .hasSize(1)
                .first()
                .extracting(b -> b.entity())
                .isEqualTo("EU");
    }

    private Long definitionId(String code) {
        ReconciliationDefinition definition = definitionRepository
                .findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing definition " + code));
        return definition.getId();
    }
}

