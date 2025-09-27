package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.ExportJob;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.ExportFormat;
import com.universal.reconciliation.domain.enums.ExportJobStatus;
import com.universal.reconciliation.domain.enums.ExportJobType;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.ExportJobRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.service.export.DatasetExportWriter;
import com.universal.reconciliation.service.search.BreakSearchCriteria;
import com.universal.reconciliation.service.search.BreakSearchCursor;
import com.universal.reconciliation.service.search.BreakSearchResult;
import com.universal.reconciliation.service.search.BreakSearchRow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

@ExtendWith(MockitoExtension.class)
class ExportJobServiceTest {

    @Mock
    private ExportJobRepository exportJobRepository;

    @Mock
    private ReconciliationDefinitionRepository definitionRepository;

    @Mock
    private BreakSearchService breakSearchService;

    @Mock
    private BreakSearchCriteriaFactory criteriaFactory;

    @Mock
    private BreakAccessService breakAccessService;

    private DatasetExportWriter datasetExportWriter;

    private ObjectMapper objectMapper;

    private ExportJobService service;

    private ReconciliationDefinition definition;

    @BeforeEach
    void setUp() {
        definition = new ReconciliationDefinition();
        definition.setId(11L);
        definition.setCode("FX-1");
        objectMapper = new ObjectMapper();
        datasetExportWriter = new DatasetExportWriter(objectMapper);
        service = new ExportJobService(
                exportJobRepository,
                definitionRepository,
                breakSearchService,
                criteriaFactory,
                breakAccessService,
                datasetExportWriter,
                objectMapper);
    }

    private void stubDefinitionAccess() {
        when(definitionRepository.findById(definition.getId())).thenReturn(Optional.of(definition));
    }

    @Test
    void queueDatasetExportShouldPersistJob() {
        stubDefinitionAccess();
        when(breakAccessService.findEntries(eq(definition), any())).thenReturn(List.of(new AccessControlEntry()));
        Map<String, List<String>> filters = Map.of("fromDate", List.of("2024-05-01"));
        ArgumentCaptor<ExportJob> captor = ArgumentCaptor.forClass(ExportJob.class);

        service.queueDatasetExport(
                definition.getId(),
                new com.universal.reconciliation.domain.dto.ExportJobRequestDto(
                        ExportFormat.CSV, filters, "ops", true),
                "owner",
                List.of("grp"));

        verify(exportJobRepository).save(captor.capture());
        ExportJob job = captor.getValue();
        assertThat(job.getFormat()).isEqualTo(ExportFormat.CSV);
        assertThat(job.getOwner()).isEqualTo("owner");
        assertThat(job.getJobType()).isEqualTo(ExportJobType.RESULT_DATASET);
        assertThat(job.getFileName()).contains("ops-");
    }

    @Test
    void processJobShouldGeneratePayloadAcrossPages() throws Exception {
        ExportJob job = new ExportJob();
        job.setId(55L);
        job.setDefinition(definition);
        job.setOwner("owner");
        job.setFormat(ExportFormat.CSV);
        job.setJobType(ExportJobType.RESULT_DATASET);
        job.setFiltersJson(objectMapper.writeValueAsString(Map.of("size", List.of("50"))));
        job.setOwnerGroupsJson(objectMapper.writeValueAsString(List.of("grp")));

        when(exportJobRepository.findById(55L)).thenReturn(Optional.of(job));
        when(exportJobRepository.save(any(ExportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<MultiValueMap<String, String>> paramsCaptor = ArgumentCaptor.forClass(MultiValueMap.class);

        BreakSearchCriteria firstCriteria = new BreakSearchCriteria(
                null,
                null,
                java.util.Set.of(),
                java.util.Set.of(),
                java.util.Set.of(BreakStatus.OPEN),
                Map.of(),
                "", // search term
                200,
                null,
                false);
        BreakSearchCriteria secondCriteria = new BreakSearchCriteria(
                null,
                null,
                java.util.Set.of(),
                java.util.Set.of(),
                java.util.Set.of(BreakStatus.OPEN),
                Map.of(),
                "",
                200,
                new BreakSearchCursor(Instant.now(), 2L),
                false);

        when(criteriaFactory.fromQueryParams(any(MultiValueMap.class)))
                .thenReturn(firstCriteria)
                .thenReturn(secondCriteria);

        BreakItemDto itemDto = new BreakItemDto(
                1L,
                BreakType.MISMATCH,
                BreakStatus.OPEN,
                Map.of("product", "FX"),
                List.of(BreakStatus.CLOSED),
                Instant.now(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                "maker",
                null,
                Instant.now());

        BreakSearchRow firstRow = new BreakSearchRow(
                1L,
                10L,
                Instant.now(),
                "Asia/Singapore",
                TriggerType.MANUAL_API,
                itemDto,
                Map.of("product", "FX"));
        BreakSearchResult firstPage = new BreakSearchResult(
                List.of(firstRow),
                new BreakSearchCursor(Instant.now(), 1L),
                true,
                1L,
                List.of());

        BreakSearchRow secondRow = new BreakSearchRow(
                2L,
                11L,
                Instant.now(),
                "Asia/Singapore",
                TriggerType.SCHEDULED_CRON,
                itemDto,
                Map.of("product", "FX"));
        BreakSearchResult secondPage =
                new BreakSearchResult(List.of(secondRow), null, false, 2L, List.of());

        when(breakSearchService.search(eq(definition.getId()), any(), any()))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        service.processJob(55L);

        verify(criteriaFactory, times(2)).fromQueryParams(paramsCaptor.capture());
        List<MultiValueMap<String, String>> captured = paramsCaptor.getAllValues();
        assertThat(captured.get(0).getFirst("size")).isEqualTo("200");
        assertThat(captured.get(1).getFirst("cursor")).isNotBlank();

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        assertThat(job.getPayload()).isNotNull();
        assertThat(job.getPayload().length).isGreaterThan(0);
        assertThat(job.getRowCount()).isEqualTo(2);
        assertThat(job.getContentHash()).isNotBlank();
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void processJobShouldSetFailureOnException() throws Exception {
        ExportJob job = new ExportJob();
        job.setId(77L);
        job.setDefinition(definition);
        job.setOwner("owner");
        job.setFormat(ExportFormat.CSV);
        job.setJobType(ExportJobType.RESULT_DATASET);
        job.setFiltersJson(objectMapper.writeValueAsString(Map.of()));
        job.setOwnerGroupsJson(objectMapper.writeValueAsString(List.of("grp")));

        when(exportJobRepository.findById(77L)).thenReturn(Optional.of(job));
        when(exportJobRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        when(criteriaFactory.fromQueryParams(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.processJob(77L)).isInstanceOf(IllegalStateException.class);

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("boom");
    }

    @Test
    void findJobShouldEnforceOwner() {
        ExportJob mine = new ExportJob();
        mine.setId(1L);
        mine.setOwner("owner");
        when(exportJobRepository.findById(1L)).thenReturn(Optional.of(mine));

        assertThat(service.findJob(1L, "owner")).contains(mine);
        assertThat(service.findJob(1L, "other")).isEmpty();
    }

    @Test
    void listJobsShouldDelegateToRepository() {
        stubDefinitionAccess();
        ExportJob job = new ExportJob();
        job.setId(1L);
        job.setDefinition(definition);
        job.setOwner("owner");
        when(exportJobRepository.findByDefinitionAndOwnerOrderByCreatedAtDesc(definition, "owner"))
                .thenReturn(List.of(job));

        List<com.universal.reconciliation.domain.dto.ExportJobDto> jobs = service.listJobs(definition.getId(), "owner");

        assertThat(jobs).hasSize(1);
        verify(exportJobRepository).findByDefinitionAndOwnerOrderByCreatedAtDesc(definition, "owner");
        verify(exportJobRepository, never()).findById(any());
    }
}
