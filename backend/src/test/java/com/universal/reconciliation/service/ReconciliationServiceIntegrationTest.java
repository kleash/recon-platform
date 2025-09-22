package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
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

    @Autowired
    private AccessControlEntryRepository accessControlEntryRepository;

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

    @Test
    void triggerRun_rejectsRequestsWithoutAccess() {
        Long definitionId = definitionId("CASH_VS_GL_SIMPLE");

        assertThatThrownBy(() -> reconciliationService.triggerRun(
                        definitionId, List.of("unauthorised"), "integration-test", new TriggerRunRequest(null, null, null, null)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void fetchLatestRun_returnsEmptySummaryWhenNoRunsExist() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setCode("REGRESSION_" + System.nanoTime());
        definition.setName("Regression Coverage");
        definition.setDescription("Created for regression testing");
        definition.setMakerCheckerEnabled(false);
        definition = definitionRepository.save(definition);

        AccessControlEntry entry = new AccessControlEntry();
        entry.setDefinition(definition);
        entry.setLdapGroupDn("regression-testers");
        entry.setRole(AccessRole.MAKER);
        accessControlEntryRepository.save(entry);

        RunDetailDto detail = reconciliationService.fetchLatestRun(
                definition.getId(), List.of("regression-testers"), BreakFilterCriteria.none());

        assertThat(detail.summary().runId()).isNull();
        assertThat(detail.summary().triggerType()).isEqualTo(TriggerType.MANUAL_API);
        assertThat(detail.breaks()).isEmpty();
        assertThat(detail.analytics().totalBreakCount()).isZero();
        assertThat(detail.filters().products()).isEmpty();
    }

    private Long definitionId(String code) {
        ReconciliationDefinition definition = definitionRepository
                .findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing definition " + code));
        return definition.getId();
    }
}

