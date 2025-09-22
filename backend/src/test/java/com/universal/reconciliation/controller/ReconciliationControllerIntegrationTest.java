package com.universal.reconciliation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.FilterMetadataDto;
import com.universal.reconciliation.domain.dto.ReconciliationListItemDto;
import com.universal.reconciliation.domain.dto.ReconciliationSummaryDto;
import com.universal.reconciliation.domain.dto.RunAnalyticsDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.service.BreakFilterCriteria;
import com.universal.reconciliation.service.ReconciliationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ReconciliationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    @Test
    @WithMockUser(username = "api-user", authorities = {"recon-makers", "recon-checkers"})
    void listReconciliations_usesGroupsFromSecurityContext() throws Exception {
        List<ReconciliationListItemDto> accessible =
                List.of(new ReconciliationListItemDto(1L, "CASH", "Cash vs GL", "Core reconciliation"));
        when(reconciliationService.listAccessible(anyList())).thenReturn(accessible);

        mockMvc.perform(get("/api/reconciliations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CASH"));

        ArgumentCaptor<List<String>> groupsCaptor = ArgumentCaptor.forClass(List.class);
        verify(reconciliationService).listAccessible(groupsCaptor.capture());
        assertThat(groupsCaptor.getValue())
                .containsExactlyInAnyOrder("recon-makers", "recon-checkers");
    }

    @Test
    @WithMockUser(username = "api-user", authorities = {"recon-makers"})
    void triggerRun_withoutBodyUsesDefaultRequest() throws Exception {
        RunDetailDto detail = sampleRunDetail();
        when(reconciliationService.triggerRun(anyLong(), anyList(), anyString(), any())).thenReturn(detail);

        mockMvc.perform(post("/api/reconciliations/{id}/run", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.runId").value(detail.summary().runId()));

        ArgumentCaptor<List<String>> groupsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<TriggerRunRequest> requestCaptor = ArgumentCaptor.forClass(TriggerRunRequest.class);
        verify(reconciliationService)
                .triggerRun(eq(42L), groupsCaptor.capture(), eq("api-user"), requestCaptor.capture());

        assertThat(groupsCaptor.getValue()).containsExactly("recon-makers");
        assertThat(requestCaptor.getValue())
                .isEqualTo(new TriggerRunRequest(null, null, null, null));
    }

    @Test
    @WithMockUser(username = "api-user", authorities = {"recon-makers"})
    void getLatestRun_translatesQueryParametersIntoFilterCriteria() throws Exception {
        RunDetailDto detail = sampleRunDetail();
        when(reconciliationService.fetchLatestRun(anyLong(), anyList(), any(BreakFilterCriteria.class)))
                .thenReturn(detail);

        mockMvc.perform(get("/api/reconciliations/{id}/runs/latest", 5L)
                        .param("product", "Payments")
                        .param("subProduct", "Wire")
                        .param("entity", "US")
                        .param("status", "OPEN")
                        .param("status", "OPEN")
                        .param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.definitionId").value(detail.summary().definitionId()));

        ArgumentCaptor<List<String>> groupsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<BreakFilterCriteria> filterCaptor = ArgumentCaptor.forClass(BreakFilterCriteria.class);
        verify(reconciliationService)
                .fetchLatestRun(eq(5L), groupsCaptor.capture(), filterCaptor.capture());

        assertThat(groupsCaptor.getValue()).containsExactly("recon-makers");
        assertThat(filterCaptor.getValue())
                .isEqualTo(new BreakFilterCriteria(
                        "Payments", "Wire", "US", Set.of(BreakStatus.OPEN, BreakStatus.CLOSED)));
    }

    @Test
    @WithMockUser(username = "api-user", authorities = {"recon-makers"})
    void getRun_buildsEmptyStatusFilterWhenNoneProvided() throws Exception {
        RunDetailDto detail = sampleRunDetail();
        when(reconciliationService.fetchRunDetail(anyLong(), anyList(), any(BreakFilterCriteria.class)))
                .thenReturn(detail);

        mockMvc.perform(get("/api/reconciliations/runs/{runId}", 7L).param("entity", "EU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.runId").value(detail.summary().runId()));

        ArgumentCaptor<List<String>> groupsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<BreakFilterCriteria> filterCaptor = ArgumentCaptor.forClass(BreakFilterCriteria.class);
        verify(reconciliationService)
                .fetchRunDetail(eq(7L), groupsCaptor.capture(), filterCaptor.capture());

        assertThat(groupsCaptor.getValue()).containsExactly("recon-makers");
        assertThat(filterCaptor.getValue())
                .isEqualTo(new BreakFilterCriteria(null, null, "EU", Set.<BreakStatus>of()));
    }

    private RunDetailDto sampleRunDetail() {
        ReconciliationSummaryDto summary = new ReconciliationSummaryDto(
                10L,
                123L,
                Instant.parse("2024-01-15T10:15:30Z"),
                TriggerType.MANUAL_API,
                "tester",
                "corr-123",
                "comments",
                5,
                3,
                2);
        BreakItemDto breakItem = new BreakItemDto(
                1L,
                BreakType.MISMATCH,
                BreakStatus.OPEN,
                "Payments",
                "Wire",
                "US",
                List.of(BreakStatus.CLOSED),
                Instant.parse("2024-01-15T10:20:30Z"),
                Map.of("amount", 100),
                Map.of("amount", 90),
                List.of());
        FilterMetadataDto metadata = new FilterMetadataDto(
                List.of("Payments"),
                List.of("Wire"),
                List.of("US"),
                List.of(BreakStatus.OPEN, BreakStatus.CLOSED));
        return new RunDetailDto(summary, RunAnalyticsDto.empty(), List.of(breakItem), metadata);
    }
}
