package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.BulkBreakUpdateRequest;
import com.universal.reconciliation.domain.dto.BulkBreakUpdateResponse;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.repository.BreakCommentRepository;
import com.universal.reconciliation.repository.BreakItemRepository;
import com.universal.reconciliation.repository.BreakWorkflowAuditRepository;
import com.universal.reconciliation.security.UserContext;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BreakServiceTest {

    private BreakItemRepository breakItemRepository;
    private BreakCommentRepository breakCommentRepository;
    private UserContext userContext;
    private UserDirectoryService userDirectoryService;
    private BreakMapper breakMapper;
    private BreakAccessService breakAccessService;
    private SystemActivityService systemActivityService;
    private BreakWorkflowAuditRepository breakWorkflowAuditRepository;
    private BreakService breakService;

    @BeforeEach
    void setUp() {
        breakItemRepository = Mockito.mock(BreakItemRepository.class);
        breakCommentRepository = Mockito.mock(BreakCommentRepository.class);
        userContext = Mockito.mock(UserContext.class);
        userDirectoryService = Mockito.mock(UserDirectoryService.class);
        breakMapper = Mockito.mock(BreakMapper.class);
        breakAccessService = Mockito.mock(BreakAccessService.class);
        systemActivityService = Mockito.mock(SystemActivityService.class);
        breakWorkflowAuditRepository = Mockito.mock(BreakWorkflowAuditRepository.class);
        breakService = new BreakService(
                breakItemRepository,
                breakCommentRepository,
                userContext,
                userDirectoryService,
                breakMapper,
                breakAccessService,
                systemActivityService,
                breakWorkflowAuditRepository);
        when(breakAccessService.scopedEntries(any(BreakItem.class), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void addComment_persistsAuditAndRecordsActivity() {
        BreakItem item = breakItem();
        when(breakItemRepository.findById(42L)).thenReturn(Optional.of(item));
        when(userContext.getGroups()).thenReturn(List.of("recon-makers"));
        when(userContext.getUsername()).thenReturn("ops1");
        when(userDirectoryService.personDn("ops1")).thenReturn("uid=ops1");
        when(breakAccessService.findEntries(any(ReconciliationDefinition.class), any()))
                .thenReturn(List.of(new AccessControlEntry()));
        when(breakAccessService.allowedStatuses(any(), any(), any())).thenReturn(List.of());
        when(breakMapper.toDto(any(), any()))
                .thenReturn(new BreakItemDto(
                        1L,
                        null,
                        BreakStatus.OPEN,
                        Map.of(),
                        List.of(),
                        null,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null));

        var response = breakService.addComment(42L, new com.universal.reconciliation.domain.dto.AddBreakCommentRequest("Investigate", "Note"));

        assertThat(response).isNotNull();
        verify(breakCommentRepository).save(any());
        verify(systemActivityService).recordEvent(any(), any());
    }

    @Test
    @DisplayName("bulkUpdate applies comment and status across requested breaks")
    void bulkUpdate_updatesMultipleBreaks() {
        BreakItem first = breakItem();
        first.setId(101L);
        BreakItem second = breakItem();
        second.setId(202L);

        when(breakItemRepository.findAllById(any())).thenReturn(List.of(first, second));
        when(userContext.getGroups()).thenReturn(List.of("recon-makers"));
        when(userContext.getUsername()).thenReturn("ops1");
        when(userDirectoryService.personDn("ops1")).thenReturn("uid=ops1");
        AccessControlEntry checkerEntry = new AccessControlEntry();
        checkerEntry.setRole(AccessRole.CHECKER);
        checkerEntry.setLdapGroupDn("cn=checker");
        List<AccessControlEntry> entries = List.of(checkerEntry);
        when(breakAccessService.findEntries(any(ReconciliationDefinition.class), any())).thenReturn(entries);
        when(breakAccessService.allowedStatuses(any(), any(), any())).thenReturn(List.of(BreakStatus.CLOSED));
        when(breakMapper.toDto(any(), any()))
                .thenReturn(new BreakItemDto(
                        1L,
                        null,
                        BreakStatus.CLOSED,
                        Map.of(),
                        List.of(),
                        null,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null));

        BulkBreakUpdateRequest request = new BulkBreakUpdateRequest(
                List.of(101L, 202L), BreakStatus.CLOSED, "Bulk closing", "BULK_CLOSE", "bulk-1");

        BulkBreakUpdateResponse responses = breakService.bulkUpdate(request);

        assertThat(responses.successes()).hasSize(2);
        assertThat(responses.failures()).isEmpty();
        verify(systemActivityService).recordEvent(any(), any());
        verify(breakItemRepository)
                .saveAll(argThat(items -> items instanceof Collection<?> collection && collection.size() == 2));
        verify(breakCommentRepository)
                .saveAll(argThat(comments -> comments instanceof Collection<?> collection && collection.size() == 2));
        verify(breakWorkflowAuditRepository)
                .saveAll(argThat(audits -> audits instanceof Collection<?> collection && collection.size() == 2));
    }

    @Test
    void bulkUpdate_returnsFailuresWhenBreakMissing() {
        when(breakItemRepository.findAllById(any())).thenReturn(List.of());

        BulkBreakUpdateRequest request = new BulkBreakUpdateRequest(
                List.of(999L), BreakStatus.CLOSED, "comment", "BULK", null);

        BulkBreakUpdateResponse response = breakService.bulkUpdate(request);

        assertThat(response.successes()).isEmpty();
        assertThat(response.failures()).hasSize(1);
        assertThat(response.failures().get(0).reason()).contains("Break not found");
    }

    private BreakItem breakItem() {
        BreakItem item = new BreakItem();
        item.setId(42L);
        item.setStatus(BreakStatus.OPEN);
        item.setProduct("Payments");
        item.setSubProduct("Wire");
        item.setEntityName("US");
        ReconciliationRun run = new ReconciliationRun();
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setMakerCheckerEnabled(true);
        run.setDefinition(definition);
        item.setRun(run);
        return item;
    }
}
