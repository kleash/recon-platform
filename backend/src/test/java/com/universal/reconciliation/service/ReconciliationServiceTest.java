package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.dto.ApprovalQueueDto;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.RunStatus;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import com.universal.reconciliation.repository.BreakItemRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationRunRepository;
import com.universal.reconciliation.service.matching.MatchingEngine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private ReconciliationDefinitionRepository definitionRepository;

    @Mock
    private AccessControlEntryRepository accessControlEntryRepository;

    @Mock
    private ReconciliationRunRepository runRepository;

    @Mock
    private BreakItemRepository breakItemRepository;

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private BreakMapper breakMapper;

    @Mock
    private BreakAccessService breakAccessService;

    @Mock
    private SystemActivityService systemActivityService;

    @Mock
    private RunAnalyticsCalculator runAnalyticsCalculator;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private ReconciliationService reconciliationService;

    private ReconciliationDefinition definition;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(
                definitionRepository,
                accessControlEntryRepository,
                runRepository,
                breakItemRepository,
                matchingEngine,
                objectMapper,
                breakMapper,
                breakAccessService,
                systemActivityService,
                runAnalyticsCalculator);

        definition = new ReconciliationDefinition();
        definition.setId(1L);
        definition.setCode("FX-DAILY");
    }

    @Test
    void shouldReturnApprovalQueueForChecker() {
        List<String> groups = List.of("recon-checkers");

        AccessControlEntry entry = new AccessControlEntry();
        entry.setDefinition(definition);
        entry.setRole(AccessRole.CHECKER);
        entry.setProduct("FX");
        entry.setSubProduct("SPOT");
        entry.setEntityName("SG");

        when(definitionRepository.findById(1L)).thenReturn(Optional.of(definition));
        when(breakAccessService.findEntries(definition, groups)).thenReturn(List.of(entry));

        ReconciliationRun run = new ReconciliationRun();
        run.setId(5L);
        run.setDefinition(definition);
        run.setRunDateTime(Instant.now());
        run.setTriggerType(TriggerType.MANUAL_API);
        run.setStatus(RunStatus.SUCCESS);
        run.setMatchedCount(0);
        run.setMismatchedCount(0);
        run.setMissingCount(0);

        BreakItem breakItem = new BreakItem();
        breakItem.setId(10L);
        breakItem.setRun(run);
        breakItem.setBreakType(BreakType.MISMATCH);
        breakItem.setStatus(BreakStatus.PENDING_APPROVAL);
        breakItem.setDetectedAt(Instant.now());
        breakItem.setProduct("FX");
        breakItem.setSubProduct("SPOT");
        breakItem.setEntityName("SG");

        when(breakItemRepository.findTop200ByRunDefinitionIdAndStatusOrderByDetectedAtAsc(1L, BreakStatus.PENDING_APPROVAL))
                .thenReturn(List.of(breakItem));
        when(breakAccessService.canView(breakItem, List.of(entry))).thenReturn(true);
        when(breakAccessService.allowedStatuses(breakItem, definition, List.of(entry)))
                .thenReturn(List.of(BreakStatus.CLOSED, BreakStatus.REJECTED));

        BreakItemDto dto = new BreakItemDto(
                10L,
                BreakType.MISMATCH,
                BreakStatus.PENDING_APPROVAL,
                java.util.Map.of("product", "FX"),
                List.of(BreakStatus.CLOSED, BreakStatus.REJECTED),
                Instant.now(),
                java.util.Map.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);
        when(breakMapper.toDto(breakItem, List.of(BreakStatus.CLOSED, BreakStatus.REJECTED))).thenReturn(dto);

        ApprovalQueueDto queue = reconciliationService.fetchApprovalQueue(1L, groups);

        assertThat(queue.pendingBreaks()).containsExactly(dto);
        assertThat(queue.filterMetadata().products()).containsExactly("FX");
        assertThat(queue.filterMetadata().subProducts()).containsExactly("SPOT");
        assertThat(queue.filterMetadata().entities()).containsExactly("SG");
    }

    @Test
    void shouldRejectWhenCheckerRoleMissing() {
        List<String> groups = List.of("recon-makers");

        AccessControlEntry entry = new AccessControlEntry();
        entry.setDefinition(definition);
        entry.setRole(AccessRole.MAKER);

        when(definitionRepository.findById(1L)).thenReturn(Optional.of(definition));
        when(breakAccessService.findEntries(definition, groups)).thenReturn(List.of(entry));

        assertThatThrownBy(() -> reconciliationService.fetchApprovalQueue(1L, groups))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Checker role required");
    }
}
