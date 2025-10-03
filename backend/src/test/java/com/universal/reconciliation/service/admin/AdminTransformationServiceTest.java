package com.universal.reconciliation.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationResponse;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestResponse;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationApplyRequest;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationApplyResponse;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationPreviewResponse;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationPreviewUploadRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationSampleResponse;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import com.universal.reconciliation.domain.entity.SourceDataRecord;
import com.universal.reconciliation.domain.enums.DataBatchStatus;
import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
import com.universal.reconciliation.service.admin.GroovyScriptAuthoringService;
import com.universal.reconciliation.service.transform.DataTransformationService;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import com.universal.reconciliation.service.transform.TransformationSampleFileService;
import com.universal.reconciliation.service.transform.SourceTransformationPlanProcessor;
import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import com.universal.reconciliation.domain.enums.GroovyScriptGenerationScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

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

    @Mock
    private TransformationSampleFileService sampleFileService;

    @Mock
    private GroovyScriptAuthoringService groovyScriptAuthoringService;

    @Mock
    private SourceTransformationPlanProcessor transformationPlanProcessor;

    private AdminTransformationService service;

    @BeforeEach
    void setUp() {
        service = new AdminTransformationService(
                transformationService,
                definitionRepository,
                sourceRepository,
                batchRepository,
                recordRepository,
                new ObjectMapper(),
                sampleFileService,
                groovyScriptAuthoringService,
                transformationPlanProcessor);
    }

    @Test
    void generateGroovyScriptDelegatesToAuthoringService() {
        GroovyScriptGenerationRequest request = new GroovyScriptGenerationRequest(
                "Trim value",
                "Trade Reference",
                null,
                "SOURCE_A",
                "trade_ref",
                " value ",
                Map.of(),
                List.of("trade_ref", "trade_date"),
                GroovyScriptGenerationScope.FIELD);
        GroovyScriptGenerationResponse generated = new GroovyScriptGenerationResponse(
                "return value?.toString()?.trim()", "Trim whitespace from trade reference");
        when(groovyScriptAuthoringService.generate(request)).thenReturn(generated);

        GroovyScriptGenerationResponse response = service.generateGroovyScript(request);

        assertThat(response).isEqualTo(generated);
        verify(transformationService).validateGroovyScript("return value?.toString()?.trim()");
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

    @Test
    void previewPlanTransformsRows() {
        SourceTransformationPlan plan = new SourceTransformationPlan();
        SourceTransformationPreviewUploadRequest request = new SourceTransformationPreviewUploadRequest(
                TransformationSampleFileType.CSV,
                true,
                ",",
                null,
                List.of(),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                plan);
        List<Map<String, Object>> rawRows = List.of(Map.of("Amount", "100"));
        List<Map<String, Object>> transformedRows = List.of(Map.of("Amount", "200"));
        MultipartFile file = Mockito.mock(MultipartFile.class);

        when(sampleFileService.parseSamples(request, file)).thenReturn(rawRows);
        Mockito.doNothing().when(transformationPlanProcessor).validate(plan);
        when(transformationPlanProcessor.apply(plan, rawRows)).thenReturn(transformedRows);

        SourceTransformationPreviewResponse response = service.previewPlan(request, file);

        assertThat(response.rawRows()).isEqualTo(rawRows);
        assertThat(response.transformedRows()).isEqualTo(transformedRows);
        verify(transformationPlanProcessor).validate(plan);
    }

    @Test
    void listSampleSheetNamesDelegatesToFileService() {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(sampleFileService.listSheetNames(file)).thenReturn(List.of("Sheet1", "Sheet2"));

        List<String> names = service.listSampleSheetNames(file);

        assertThat(names).containsExactly("Sheet1", "Sheet2");
        verify(sampleFileService).listSheetNames(file);
    }

    @Test
    void applyPlanDelegatesToProcessor() {
        SourceTransformationPlan plan = new SourceTransformationPlan();
        List<Map<String, Object>> rows = List.of(Map.of("Amount", "100"));
        List<Map<String, Object>> transformed = List.of(Map.of("Amount", "110"));

        Mockito.doNothing().when(transformationPlanProcessor).validate(plan);
        when(transformationPlanProcessor.apply(plan, rows)).thenReturn(transformed);

        SourceTransformationApplyRequest request = new SourceTransformationApplyRequest(plan, rows);
        SourceTransformationApplyResponse response = service.applyPlan(request);

        assertThat(response.transformedRows()).isEqualTo(transformed);
        verify(transformationPlanProcessor).validate(plan);
    }

}
