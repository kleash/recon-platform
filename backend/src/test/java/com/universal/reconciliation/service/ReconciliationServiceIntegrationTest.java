package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.TriggerType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReconciliationServiceIntegrationTest {

    @Autowired
    private ReconciliationService reconciliationService;

    private final List<String> groups = List.of("recon-makers", "recon-checkers");

    @Test
    void fetchLatestRun_returnsFilteredBreaksAndMetadata() {
        RunDetailDto initial = reconciliationService.triggerRun(
                1L, groups, "integration-test", new TriggerRunRequest(TriggerType.MANUAL_API, "it-run", "integration test", null));
        assertThat(initial.summary().runId()).isNotNull();
        assertThat(initial.summary().triggerType()).isEqualTo(TriggerType.MANUAL_API);
        assertThat(initial.analytics().totalBreakCount()).isGreaterThan(0);

        RunDetailDto usBreaks = reconciliationService.fetchLatestRun(
                1L,
                groups,
                new BreakFilterCriteria("Payments", "Wire", "US", Set.of(BreakStatus.OPEN)));

        assertThat(usBreaks.breaks())
                .allSatisfy(breakItem -> assertThat(breakItem.entity()).isEqualTo("US"));
        assertThat(usBreaks.filters().products()).contains("Payments");
        assertThat(usBreaks.analytics().breaksByStatus()).containsKey(BreakStatus.OPEN.name());

        RunDetailDto euOnly = reconciliationService.fetchLatestRun(
                1L,
                groups,
                new BreakFilterCriteria(null, null, "EU", Set.of(BreakStatus.OPEN)));

        assertThat(euOnly.breaks())
                .hasSize(1)
                .first()
                .extracting(b -> b.entity())
                .isEqualTo("EU");
    }
}

