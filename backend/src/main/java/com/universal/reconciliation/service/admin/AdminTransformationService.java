package com.universal.reconciliation.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.universal.reconciliation.domain.dto.admin.TransformationValidationRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationResponse;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import com.universal.reconciliation.domain.entity.SourceDataRecord;
import com.universal.reconciliation.domain.enums.DataBatchStatus;
import com.universal.reconciliation.service.transform.DataTransformationService;
import com.universal.reconciliation.service.transform.SourceTransformationPlanProcessor;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import com.universal.reconciliation.service.transform.TransformationSampleFileService;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
import jakarta.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminTransformationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DataTransformationService transformationService;
    private final ReconciliationDefinitionRepository definitionRepository;
    private final ReconciliationSourceRepository sourceRepository;
    private final SourceDataBatchRepository batchRepository;
    private final SourceDataRecordRepository recordRepository;
    private final ObjectMapper objectMapper;
    private final TransformationSampleFileService sampleFileService;
    private final GroovyScriptAuthoringService groovyScriptAuthoringService;
    private final SourceTransformationPlanProcessor transformationPlanProcessor;

    public AdminTransformationService(
            DataTransformationService transformationService,
            ReconciliationDefinitionRepository definitionRepository,
            ReconciliationSourceRepository sourceRepository,
            SourceDataBatchRepository batchRepository,
            SourceDataRecordRepository recordRepository,
            ObjectMapper objectMapper,
            TransformationSampleFileService sampleFileService,
            GroovyScriptAuthoringService groovyScriptAuthoringService,
            SourceTransformationPlanProcessor transformationPlanProcessor) {
        this.transformationService = transformationService;
        this.definitionRepository = definitionRepository;
        this.sourceRepository = sourceRepository;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
        this.sampleFileService = sampleFileService;
        this.groovyScriptAuthoringService = groovyScriptAuthoringService;
        this.transformationPlanProcessor = transformationPlanProcessor;
    }

    public TransformationValidationResponse validate(@Valid TransformationValidationRequest request) {
        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setType(request.type());
        transformation.setExpression(trimToNull(request.expression()));
        transformation.setConfiguration(trimToNull(request.configuration()));
        try {
            transformationService.validate(transformation);
            return new TransformationValidationResponse(true, "Transformation is valid");
        } catch (TransformationEvaluationException ex) {
            return new TransformationValidationResponse(false, ex.getMessage());
        }
    }

    public GroovyScriptGenerationResponse generateGroovyScript(@Valid GroovyScriptGenerationRequest request) {
        GroovyScriptGenerationResponse response = groovyScriptAuthoringService.generate(request);
        transformationService.validateGroovyScript(response.script());
        return response;
    }

    public GroovyScriptTestResponse testGroovyScript(@Valid GroovyScriptTestRequest request) {
        transformationService.validateGroovyScript(request.script());
        Object result = transformationService.evaluateGroovyScript(
                request.script(), request.value(), request.rawRecord() == null ? Map.of() : request.rawRecord());
        return new GroovyScriptTestResponse(result);
    }

    public SourceTransformationPreviewResponse previewPlan(
            @Valid SourceTransformationPreviewUploadRequest request, MultipartFile file) {
        List<Map<String, Object>> rawRows = sampleFileService.parseSamples(request, file);
        var plan = request.transformationPlan();
        transformationPlanProcessor.validate(plan);
        List<Map<String, Object>> transformedRows = transformationPlanProcessor.apply(plan, rawRows);
        return new SourceTransformationPreviewResponse(rawRows, transformedRows);
    }

    public List<String> listSampleSheetNames(MultipartFile file) {
        return sampleFileService.listSheetNames(file);
    }

    public SourceTransformationApplyResponse applyPlan(@Valid SourceTransformationApplyRequest request) {
        var plan = request.transformationPlan();
        transformationPlanProcessor.validate(plan);
        List<Map<String, Object>> transformed = transformationPlanProcessor.apply(plan, request.rows());
        return new SourceTransformationApplyResponse(transformed);
    }

    public TransformationSampleResponse loadSampleRows(long definitionId, String sourceCode, int limit) {
        if (limit <= 0) {
            limit = 5;
        }
        int fetchSize = Math.min(limit, 50);

        ReconciliationDefinition definition = definitionRepository
                .findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reconciliation definition " + definitionId));
        ReconciliationSource source = sourceRepository
                .findByDefinitionAndCode(definition, sourceCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown source code " + sourceCode));

        Optional<SourceDataBatch> latestCompleteBatch = batchRepository.findBySourceOrderByIngestedAtDesc(source).stream()
                .filter(batch -> batch.getStatus() == DataBatchStatus.COMPLETE)
                .findFirst();

        SourceDataBatch batch = latestCompleteBatch.orElseThrow(
                () -> new IllegalStateException("No completed batches found for source " + sourceCode));

        List<SourceDataRecord> records = recordRepository.findByBatch(batch).stream()
                .limit(fetchSize)
                .toList();

        List<TransformationSampleResponse.Row> rows = records.stream()
                .map(record -> new TransformationSampleResponse.Row(
                        record.getId(),
                        batch.getLabel(),
                        record.getIngestedAt() != null
                                ? DateTimeFormatter.ISO_INSTANT.format(record.getIngestedAt())
                                : null,
                        record.getCanonicalKey(),
                        record.getExternalReference(),
                        parseJson(record.getMetadataJson()),
                        parseJson(record.getPayloadJson())))
                .collect(Collectors.toList());

        return new TransformationSampleResponse(rows);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> parseJson(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse stored payload", ex);
        }
    }
}
