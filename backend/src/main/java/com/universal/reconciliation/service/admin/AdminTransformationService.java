package com.universal.reconciliation.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationResponse;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationFilePreviewResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationFilePreviewUploadRequest;
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
import com.universal.reconciliation.service.transform.TransformationSampleFileService;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
import jakarta.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
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

    public AdminTransformationService(
            DataTransformationService transformationService,
            ReconciliationDefinitionRepository definitionRepository,
            ReconciliationSourceRepository sourceRepository,
            SourceDataBatchRepository batchRepository,
            SourceDataRecordRepository recordRepository,
            ObjectMapper objectMapper,
            TransformationSampleFileService sampleFileService,
            GroovyScriptAuthoringService groovyScriptAuthoringService) {
        this.transformationService = transformationService;
        this.definitionRepository = definitionRepository;
        this.sourceRepository = sourceRepository;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
        this.sampleFileService = sampleFileService;
        this.groovyScriptAuthoringService = groovyScriptAuthoringService;
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
        CanonicalFieldMapping mapping = buildMappingFromDtos(request.transformations());
        try {
            Object result = transformationService.applyTransformations(
                    mapping, request.value(), request.rawRecord() == null ? Map.of() : request.rawRecord());
            return new TransformationPreviewResponse(result);
        } catch (TransformationEvaluationException ex) {
            throw ex;
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

    public TransformationFilePreviewResponse previewFromSampleFile(
            @Valid TransformationFilePreviewUploadRequest request, MultipartFile file) {
        CanonicalFieldMapping mapping = buildMappingFromDtos(request.transformations());
        List<Map<String, Object>> parsedRows = sampleFileService.parseSamples(request, file);
        List<TransformationFilePreviewResponse.Row> rows = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> raw : parsedRows) {
            Map<String, Object> safeRaw = new LinkedHashMap<>(raw);
            Object value = resolveValue(safeRaw, request.valueColumn());
            try {
                Object transformed = transformationService.applyTransformations(mapping, value, safeRaw);
                rows.add(new TransformationFilePreviewResponse.Row(index++, safeRaw, value, transformed, null));
            } catch (TransformationEvaluationException ex) {
                rows.add(new TransformationFilePreviewResponse.Row(index++, safeRaw, value, null, ex.getMessage()));
            } catch (Exception ex) {
                rows.add(new TransformationFilePreviewResponse.Row(
                        index++, safeRaw, value, null, "Transformation failed: " + ex.getMessage()));
            }
        }
        return new TransformationFilePreviewResponse(rows);
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

    private CanonicalFieldMapping buildMappingFromDtos(
            List<TransformationPreviewRequest.PreviewTransformationDto> transformations) {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setTransformations(new LinkedHashSet<>());
        if (transformations == null || transformations.isEmpty()) {
            return mapping;
        }
        transformations.stream()
                .sorted(Comparator.comparing(
                        TransformationPreviewRequest.PreviewTransformationDto::displayOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .forEach(dto -> {
                    CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
                    transformation.setMapping(mapping);
                    transformation.setType(dto.type());
                    transformation.setExpression(trimToNull(dto.expression()));
                    transformation.setConfiguration(trimToNull(dto.configuration()));
                    transformation.setDisplayOrder(dto.displayOrder());
                    transformation.setActive(dto.active() == null || dto.active());
                    mapping.getTransformations().add(transformation);
                });
        return mapping;
    }

    private Object resolveValue(Map<String, Object> rawRecord, String valueColumn) {
        if (rawRecord == null || rawRecord.isEmpty() || !StringUtils.hasText(valueColumn)) {
            return null;
        }
        if (rawRecord.containsKey(valueColumn)) {
            return rawRecord.get(valueColumn);
        }
        String normalised = normaliseColumnKey(valueColumn);
        return rawRecord.entrySet().stream()
                .filter(entry -> normaliseColumnKey(entry.getKey()).equals(normalised))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String normaliseColumnKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        return key.replaceAll("\\s+", "").toLowerCase(Locale.ENGLISH);
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
