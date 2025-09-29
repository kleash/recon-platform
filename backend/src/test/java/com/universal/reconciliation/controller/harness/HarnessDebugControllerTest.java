package com.universal.reconciliation.controller.harness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.repository.BreakItemRepository;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.BreakAccessService;
import com.universal.reconciliation.service.BreakService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class HarnessDebugControllerTest {

    @Mock
    private BreakItemRepository breakItemRepository;

    @Mock
    private BreakAccessService breakAccessService;

    @Mock
    private UserContext userContext;

    @Mock
    private BreakService breakService;

    private MockMvc mockMvc;

    private BreakItem breakItem;
    private ReconciliationDefinition definition;
    private List<String> groups;

    @BeforeEach
    void setUp() {
        HarnessDebugController controller =
                new HarnessDebugController(breakItemRepository, breakAccessService, userContext, breakService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        definition = new ReconciliationDefinition();
        definition.setId(42L);
        definition.setCode("DEF-01");
        definition.setName("Sample");
        definition.setDescription("Harness definition");
        definition.setMakerCheckerEnabled(true);

        ReconciliationRun run = new ReconciliationRun();
        run.setId(200L);
        run.setDefinition(definition);

        breakItem = new BreakItem();
        breakItem.setId(1L);
        breakItem.setRun(run);
        breakItem.setStatus(BreakStatus.OPEN);
        breakItem.setProduct("Payments");
        breakItem.setSubProduct("Wire");
        breakItem.setEntityName("US");

        groups = List.of("recon-makers", "recon-checkers");
    }

    @Test
    void inspectBreakEntriesReturnsAccessSummary() throws Exception {
        when(breakItemRepository.findById(1L)).thenReturn(Optional.of(breakItem));
        when(userContext.getGroups()).thenReturn(groups);

        AccessControlEntry makerEntry = new AccessControlEntry();
        makerEntry.setRole(AccessRole.MAKER);
        makerEntry.setLdapGroupDn("recon-makers");
        makerEntry.setDefinition(definition);

        AccessControlEntry checkerEntry = new AccessControlEntry();
        checkerEntry.setRole(AccessRole.CHECKER);
        checkerEntry.setLdapGroupDn("recon-checkers");
        checkerEntry.setDefinition(definition);

        List<AccessControlEntry> entries = List.of(makerEntry, checkerEntry);

        when(breakAccessService.findEntries(definition, groups)).thenReturn(entries);
        when(breakAccessService.scopedEntries(breakItem, entries)).thenReturn(entries);
        when(breakAccessService.allowedStatuses(breakItem, definition, entries))
                .thenReturn(List.of(BreakStatus.PENDING_APPROVAL));

        mockMvc.perform(get("/api/harness/breaks/{id}/entries", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("Payments"))
                .andExpect(jsonPath("$.allowedStatuses[0]").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.makerRole").value(true))
                .andExpect(jsonPath("$.checkerRole").value(true));
    }

    @Test
    void inspectBreakEntriesHandlesAccessDenied() throws Exception {
        when(breakItemRepository.findById(1L)).thenReturn(Optional.of(breakItem));
        when(userContext.getGroups()).thenReturn(groups);
        when(breakAccessService.findEntries(definition, groups))
                .thenThrow(new AccessDeniedException("Denied"));

        mockMvc.perform(get("/api/harness/breaks/{id}/entries", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("Denied"));
    }

    @Test
    void attemptStatusUpdateReturnsDto() throws Exception {
        BreakItemDto dto = new BreakItemDto(
                1L,
                BreakType.MISMATCH,
                BreakStatus.PENDING_APPROVAL,
                Map.of(),
                List.of(BreakStatus.PENDING_APPROVAL),
                Instant.parse("2024-01-01T00:00:00Z"),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);

        when(breakService.updateStatus(eq(1L), any())).thenReturn(dto);

        mockMvc.perform(post("/api/harness/breaks/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"pending_approval\",\"comment\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING_APPROVAL"));
    }

    @Test
    void attemptStatusUpdateHandlesException() throws Exception {
        when(breakService.updateStatus(eq(1L), any())).thenThrow(new IllegalStateException("Not allowed"));

        mockMvc.perform(post("/api/harness/breaks/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(IllegalStateException.class.getName()))
                .andExpect(jsonPath("$.message").value("Not allowed"));
    }
}

