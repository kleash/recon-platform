package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class BreakAccessServiceTest {

    private BreakAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new BreakAccessService(Mockito.mock(com.universal.reconciliation.repository.AccessControlEntryRepository.class));
    }

    @Test
    void allowedStatuses_returnsMakerTransitionsWhenWorkflowEnabled() {
        BreakItem breakItem = breakItem(BreakStatus.OPEN);
        ReconciliationDefinition definition = definition(true);
        List<AccessControlEntry> entries = List.of(entry(AccessRole.MAKER));

        assertThat(accessService.allowedStatuses(breakItem, definition, entries))
                .containsExactly(BreakStatus.PENDING_APPROVAL);
    }

    @Test
    void allowedStatuses_returnsCheckerTransitionsWhenPendingApproval() {
        BreakItem breakItem = breakItem(BreakStatus.PENDING_APPROVAL);
        ReconciliationDefinition definition = definition(true);
        List<AccessControlEntry> entries = List.of(entry(AccessRole.CHECKER));

        assertThat(accessService.allowedStatuses(breakItem, definition, entries))
                .containsExactlyInAnyOrder(BreakStatus.CLOSED, BreakStatus.REJECTED);
    }

    @Test
    void allowedStatuses_treatsDualRoleAsMakerOnly() {
        BreakItem breakItem = breakItem(BreakStatus.PENDING_APPROVAL);
        ReconciliationDefinition definition = definition(true);
        List<AccessControlEntry> entries = List.of(entry(AccessRole.MAKER), entry(AccessRole.CHECKER));

        assertThat(accessService.allowedStatuses(breakItem, definition, entries))
                .containsExactly(BreakStatus.OPEN);
    }

    @Test
    void assertTransitionAllowed_rejectsUnauthorizedTransition() {
        BreakItem breakItem = breakItem(BreakStatus.OPEN);
        ReconciliationDefinition definition = definition(true);

        assertThatThrownBy(() -> accessService.assertTransitionAllowed(breakItem, definition, List.of(), BreakStatus.CLOSED))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void canComment_requiresMakerOrCheckerRole() {
        BreakItem breakItem = breakItem(BreakStatus.OPEN);

        assertThat(accessService.canComment(breakItem, List.of(entry(AccessRole.VIEWER)))).isFalse();
        assertThat(accessService.canComment(breakItem, List.of(entry(AccessRole.MAKER)))).isTrue();
    }

    private BreakItem breakItem(BreakStatus status) {
        BreakItem item = new BreakItem();
        item.setStatus(status);
        item.setProduct("Payments");
        item.setSubProduct("Wire");
        item.setEntityName("US");
        item.setDetectedAt(Instant.now());
        return item;
    }

    private AccessControlEntry entry(AccessRole role) {
        AccessControlEntry entry = new AccessControlEntry();
        entry.setProduct("Payments");
        entry.setSubProduct("Wire");
        entry.setEntityName("US");
        entry.setRole(role);
        return entry;
    }

    private ReconciliationDefinition definition(boolean makerCheckerEnabled) {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setMakerCheckerEnabled(makerCheckerEnabled);
        return definition;
    }
}

