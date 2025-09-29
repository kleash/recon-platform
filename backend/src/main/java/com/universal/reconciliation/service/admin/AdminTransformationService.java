package com.universal.reconciliation.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationPreviewRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationPreviewResponse;
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
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
import jakarta.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminTransformationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DataTransformationService transformationService;
    private final ReconciliationDefinitionRepository definitionRepository;
    private final ReconciliationSourceRepository sourceRepository;
    private final SourceDataBatchRepository batchRepository;
    private final SourceDataRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    public AdminTransformationService(
            DataTransformationService transformationService,
            ReconciliationDefinitionRepository definitionRepository,
            ReconciliationSourceRepository sourceRepository,
            SourceDataBatchRepository batchRepository,
            SourceDataRecordRepository recordRepository,
            ObjectMapper objectMapper) {
        this.transformationService = transformationService;
        this.definitionRepository = definitionRepository;
        this.sourceRepository = sourceRepository;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
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

    public TransformationPreviewResponse preview(@Valid TransformationPreviewRequest request) {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setTransformations(new LinkedHashSet<>());
        request.transformations().stream()
                .sorted(Comparator.comparing(
                        TransformationPreviewRequest.PreviewTransformationDto::displayOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .forEach(transformationDto -> {
                    CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
                    transformation.setMapping(mapping);
                    transformation.setType(transformationDto.type());
                    transformation.setExpression(trimToNull(transformationDto.expression()));
                    transformation.setConfiguration(trimToNull(transformationDto.configuration()));
                    transformation.setDisplayOrder(transformationDto.displayOrder());
                    transformation.setActive(transformationDto.active() == null || transformationDto.active());
                    mapping.getTransformations().add(transformation);
                });
        try {
            Object result = transformationService.applyTransformations(
                    mapping, request.value(), request.rawRecord() == null ? Map.of() : request.rawRecord());
            return new TransformationPreviewResponse(result);
        } catch (TransformationEvaluationException ex) {
            throw ex;
        }
    }

    public GroovyScriptTestResponse testGroovyScript(@Valid GroovyScriptTestRequest request) {
        transformationService.validateGroovyScript(request.script());
        Object result = transformationService.evaluateGroovyScript(
                request.script(), request.value(), request.rawRecord() == null ? Map.of() : request.rawRecord());
        return new GroovyScriptTestResponse(result);
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
