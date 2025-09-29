package com.universal.reconciliation.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationSampleResponse;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import com.universal.reconciliation.domain.entity.SourceDataRecord;
import com.universal.reconciliation.domain.enums.DataBatchStatus;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
import com.universal.reconciliation.service.transform.DataTransformationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminTransformationServiceTest {

    @Mock
    private DataTransformationService transformationService;

    @Mock
    private ReconciliationDefinitionRepository definitionRepository;

    @Mock
    private ReconciliationSourceRepository sourceRepository;

    @Mock
    private SourceDataBatchRepository batchRepository;

    @Mock
    private SourceDataRecordRepository recordRepository;

    private AdminTransformationService service;

    @BeforeEach
    void setUp() {
        service = new AdminTransformationService(
                transformationService,
                definitionRepository,
                sourceRepository,
                batchRepository,
                recordRepository,
                new ObjectMapper());
    }

    @Test
    void testGroovyScriptDelegatesToTransformationService() {
        when(transformationService.evaluateGroovyScript(eq("return value"), eq("10"), anyMap())).thenReturn("10");

        GroovyScriptTestRequest request = new GroovyScriptTestRequest("return value", "10", Map.of("foo", "bar"));
        GroovyScriptTestResponse response = service.testGroovyScript(request);

        assertThat(response.result()).isEqualTo("10");
        verify(transformationService).validateGroovyScript("return value");
        verify(transformationService).evaluateGroovyScript("return value", "10", Map.of("foo", "bar"));
    }

    @Test
    void loadSampleRowsReturnsLatestCompletedBatchRecords() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setId(1L);

        ReconciliationSource source = new ReconciliationSource();
        source.setCode("CASH");

        SourceDataBatch latestBatch = new SourceDataBatch();
        latestBatch.setStatus(DataBatchStatus.COMPLETE);
        latestBatch.setLabel("latest-batch");
        latestBatch.setIngestedAt(Instant.parse("2024-01-02T00:00:00Z"));

        SourceDataRecord record = new SourceDataRecord();
        record.setBatch(latestBatch);
        record.setCanonicalKey("KEY-1");
        record.setExternalReference("REF-1");
        record.setIngestedAt(Instant.parse("2024-01-02T00:00:00Z"));
        record.setPayloadJson("{\"canonical\":1}");
        record.setMetadataJson("{\"amount\":\"100\"}");

        when(definitionRepository.findById(1L)).thenReturn(Optional.of(definition));
        when(sourceRepository.findByDefinitionAndCode(definition, "CASH")).thenReturn(Optional.of(source));
        when(batchRepository.findBySourceOrderByIngestedAtDesc(source)).thenReturn(List.of(latestBatch));
        when(recordRepository.findByBatch(latestBatch)).thenReturn(List.of(record));

        TransformationSampleResponse response = service.loadSampleRows(1L, "CASH", 5);

        assertThat(response.rows()).hasSize(1);
        TransformationSampleResponse.Row row = response.rows().get(0);
        assertThat(row.batchLabel()).isEqualTo("latest-batch");
        assertThat(row.rawRecord()).containsEntry("amount", "100");
        assertThat(row.canonicalPayload()).containsEntry("canonical", 1);
    }
}
