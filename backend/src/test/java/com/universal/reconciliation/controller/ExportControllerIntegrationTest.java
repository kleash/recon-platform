package com.universal.reconciliation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.FilterMetadataDto;
import com.universal.reconciliation.domain.dto.ReconciliationSummaryDto;
import com.universal.reconciliation.domain.dto.RunAnalyticsDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.service.ExportService;
import com.universal.reconciliation.service.ReconciliationService;
import com.universal.reconciliation.service.SystemActivityService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ExportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    @MockBean
    private ExportService exportService;

    @MockBean
    private SystemActivityService systemActivityService;

    @Test
    @WithMockUser(username = "exporter", authorities = {"recon-makers"})
    void exportRun_streamsExcelAndAuditsUser() throws Exception {
        RunDetailDto detail = sampleRunDetail();
        when(reconciliationService.fetchRunDetail(eq(44L), anyList())).thenReturn(detail);
        byte[] excel = "excel-content".getBytes(StandardCharsets.UTF_8);
        when(exportService.exportToExcel(detail)).thenReturn(excel);

        mockMvc.perform(get("/api/exports/runs/{runId}", 44L))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reconciliation-run-44.xlsx"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(excel));

        ArgumentCaptor<List<String>> groupsCaptor = ArgumentCaptor.forClass(List.class);
        verify(reconciliationService).fetchRunDetail(eq(44L), groupsCaptor.capture());
        assertThat(groupsCaptor.getValue()).containsExactly("recon-makers");

        verify(exportService).exportToExcel(detail);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(systemActivityService).recordEvent(eq(SystemEventType.REPORT_EXPORT), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("exporter");
    }

    private RunDetailDto sampleRunDetail() {
        ReconciliationSummaryDto summary = new ReconciliationSummaryDto(
                10L,
                20L,
                Instant.parse("2024-01-15T10:15:30Z"),
                TriggerType.MANUAL_API,
                "tester",
                "corr",
                "comments",
                1,
                2,
                3);
        BreakItemDto breakItem = new BreakItemDto(
                1L,
                BreakType.MISMATCH,
                BreakStatus.OPEN,
                "Payments",
                "Wire",
                "US",
                List.of(BreakStatus.PENDING_APPROVAL, BreakStatus.CLOSED),
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
