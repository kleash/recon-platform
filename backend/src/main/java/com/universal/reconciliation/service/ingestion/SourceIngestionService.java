package com.universal.reconciliation.service.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import com.universal.reconciliation.domain.entity.SourceDataRecord;
import com.universal.reconciliation.domain.enums.DataBatchStatus;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.repository.CanonicalFieldMappingRepository;
import com.universal.reconciliation.repository.CanonicalFieldRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.universal.reconciliation.service.transform.DataTransformationService;
import com.universal.reconciliation.service.transform.SourceTransformationPlanMapper;
import com.universal.reconciliation.service.transform.SourceTransformationPlanProcessor;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import com.universal.reconciliation.util.ParsingUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Coordinates ingestion across configured sources using pluggable adapters
 * and canonical field mappings.
 */
@Service
public class SourceIngestionService {

    private final ReconciliationSourceRepository sourceRepository;
    private final CanonicalFieldRepository canonicalFieldRepository;
    private final CanonicalFieldMappingRepository mappingRepository;
    private final SourceDataBatchRepository batchRepository;
    private final SourceDataRecordRepository recordRepository;
    private final Map<IngestionAdapterType, IngestionAdapter> adapters;
    private final ObjectMapper objectMapper;
    private final DataTransformationService transformationService;
    private final SourceTransformationPlanMapper transformationPlanMapper;
    private final SourceTransformationPlanProcessor transformationPlanProcessor;

    public SourceIngestionService(
            ReconciliationSourceRepository sourceRepository,
            CanonicalFieldRepository canonicalFieldRepository,
            CanonicalFieldMappingRepository mappingRepository,
            SourceDataBatchRepository batchRepository,
            SourceDataRecordRepository recordRepository,
            List<IngestionAdapter> adapters,
            ObjectMapper objectMapper,
            DataTransformationService transformationService,
            SourceTransformationPlanMapper transformationPlanMapper,
            SourceTransformationPlanProcessor transformationPlanProcessor) {
        this.sourceRepository = sourceRepository;
        this.canonicalFieldRepository = canonicalFieldRepository;
        this.mappingRepository = mappingRepository;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.adapters = adapters.stream().collect(Collectors.toMap(IngestionAdapter::getType, Function.identity()));
        this.objectMapper = objectMapper;
        this.transformationService = transformationService;
        this.transformationPlanMapper = transformationPlanMapper;
        this.transformationPlanProcessor = transformationPlanProcessor;
    }

    @Transactional
    public SourceDataBatch ingest(
            ReconciliationDefinition definition,
            String sourceCode,
            IngestionAdapterType adapterType,
            IngestionAdapterRequest request) {
        ReconciliationSource source = sourceRepository
                .findByDefinitionAndCode(definition, sourceCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown source code " + sourceCode));
        if (!source.getAdapterType().equals(adapterType)) {
            throw new IllegalArgumentException(
                    "Configured adapter " + source.getAdapterType() + " does not match requested type " + adapterType);
        }

        IngestionAdapter adapter = Optional.ofNullable(adapters.get(adapterType))
                .orElseThrow(() -> new IllegalStateException("No adapter registered for " + adapterType));

        List<Map<String, Object>> rawRecords = adapter.readRecords(request);
        var transformationPlan = transformationPlanMapper.deserialize(source.getTransformationPlan()).orElse(null);
        List<Map<String, Object>> preparedRecords = transformationPlanProcessor.apply(transformationPlan, rawRecords);
        List<CanonicalField> canonicalFields = canonicalFieldRepository.findByDefinitionOrderByDisplayOrderAsc(definition);
        Map<Long, CanonicalFieldMapping> mappingByFieldId = mappingRepository.findBySource(source).stream()
                .collect(Collectors.toMap(mapping -> mapping.getCanonicalField().getId(), Function.identity()));

        SourceDataBatch batch = new SourceDataBatch();
        batch.setSource(source);
        batch.setStatus(DataBatchStatus.LOADING);
        batch.setLabel(resolveBatchLabel(request));
        batch = batchRepository.save(batch);

        List<CanonicalField> keyFields = canonicalFields.stream()
                .filter(field -> FieldRole.KEY.equals(field.getRole()))
                .toList();

        List<SourceDataRecord> records = new ArrayList<>();
        for (Map<String, Object> rawRecord : preparedRecords) {
            Map<String, Object> canonicalPayload = projectRecord(canonicalFields, mappingByFieldId, rawRecord);
            String canonicalKey = buildCanonicalKey(keyFields, canonicalPayload);

            SourceDataRecord record = new SourceDataRecord();
            record.setBatch(batch);
            record.setCanonicalKey(canonicalKey);
            record.setExternalReference(resolveExternalReference(keyFields, canonicalPayload, rawRecord));
            record.setPayloadJson(writeJson(canonicalPayload));
            record.setMetadataJson(writeJson(rawRecord));
            records.add(record);
        }

        recordRepository.saveAll(records);

        batch.setStatus(DataBatchStatus.COMPLETE);
        batch.setRecordCount((long) records.size());
        batch.setChecksum(UUID.randomUUID().toString());
        batch = batchRepository.save(batch);
        source.getBatches().add(batch);
        return batch;
    }

    private String resolveBatchLabel(IngestionAdapterRequest request) {
        if (request.options() == null) {
            return "batch-" + Instant.now();
        }
        Object label = request.options().get("label");
        if (label instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return "batch-" + Instant.now();
    }

    private Map<String, Object> projectRecord(
            List<CanonicalField> canonicalFields,
            Map<Long, CanonicalFieldMapping> mappingByFieldId,
            Map<String, Object> rawRecord) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (CanonicalField field : canonicalFields) {
            CanonicalFieldMapping mapping = mappingByFieldId.get(field.getId());
            Object rawValue = null;
            if (mapping != null) {
                rawValue = rawRecord.get(mapping.getSourceColumn());
                if ((rawValue == null || (rawValue instanceof String str && !StringUtils.hasText(str)))
                        && mapping.getDefaultValue() != null) {
                    rawValue = mapping.getDefaultValue();
                }
                try {
                    rawValue = transformationService.applyTransformations(mapping, rawValue, rawRecord);
                } catch (TransformationEvaluationException ex) {
                    throw new IllegalArgumentException(
                            "Transformation failed for field " + field.getCanonicalName() + ": " + ex.getMessage(), ex);
                }
                rawValue = normalizeDateWithMapping(rawValue, mapping, field);
            }
            Object normalised = convert(rawValue, field);
            payload.put(field.getCanonicalName(), normalised);
        }
        return payload;
    }

    private Object normalizeDateWithMapping(Object rawValue, CanonicalFieldMapping mapping, CanonicalField field) {
        if (rawValue == null || mapping == null) {
            return rawValue;
        }
        if (!StringUtils.hasText(mapping.getSourceDateFormat())) {
            return rawValue;
        }
        FieldDataType dataType = field.getDataType();
        String sourceFormat = mapping.getSourceDateFormat();
        try {
            if (dataType == FieldDataType.DATE) {
                DateTimeFormatter input = DateTimeFormatter.ofPattern(sourceFormat);
                DateTimeFormatter output = StringUtils.hasText(mapping.getTargetDateFormat())
                        ? DateTimeFormatter.ofPattern(mapping.getTargetDateFormat())
                        : DateTimeFormatter.ISO_LOCAL_DATE;
                LocalDate parsed = LocalDate.parse(rawValue.toString(), input);
                return parsed.format(output);
            }
            if (dataType == FieldDataType.DATETIME) {
                DateTimeFormatter input = DateTimeFormatter.ofPattern(sourceFormat);
                DateTimeFormatter output = StringUtils.hasText(mapping.getTargetDateFormat())
                        ? DateTimeFormatter.ofPattern(mapping.getTargetDateFormat())
                        : DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                LocalDateTime parsed = LocalDateTime.parse(rawValue.toString(), input);
                return parsed.format(output);
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Unable to normalise date value '"
                            + rawValue
                            + "' for field "
                            + field.getCanonicalName()
                            + " using format "
                            + sourceFormat,
                    ex);
        }
        return rawValue;
    }

    private Object convert(Object rawValue, CanonicalField field) {
        if (rawValue == null) {
            if (field.isRequired()) {
                throw new IllegalArgumentException(
                        "Missing required value for canonical field " + field.getCanonicalName());
            }
            return null;
        }
        if (rawValue instanceof String rawString) {
            if (!StringUtils.hasText(rawString)) {
                return null;
            }
            rawValue = rawString.trim();
        }
        FieldDataType dataType = field.getDataType();
        return switch (dataType) {
            case STRING -> rawValue.toString();
            case DECIMAL, INTEGER -> new java.math.BigDecimal(rawValue.toString());
            case DATE -> parseDate(rawValue.toString());
            case DATETIME -> parseDateTime(rawValue.toString());
            case BOOLEAN -> parseBoolean(rawValue);
        };
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Unable to parse date value: " + value, ex);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Unable to parse datetime value: " + value, ex);
        }
    }

    private Boolean parseBoolean(Object value) {
        return ParsingUtils.parseFlexibleBoolean(value);
    }

    private String buildCanonicalKey(List<CanonicalField> keyFields, Map<String, Object> payload) {
        return keyFields.stream()
                .map(field -> Objects.toString(payload.get(field.getCanonicalName()), ""))
                .collect(Collectors.joining("|"));
    }

    /**
     * Resolve the external reference identifier for a raw record.
     *
     * <p>The service first prefers the value of the first configured {@link FieldRole#KEY}
     * field after canonical transformation. If that field is null or blank, the
     * method falls back to a raw column literally named {@code externalReference}.
     * Documenting the implicit fallback helps future maintainers understand why
     * the raw payload is consulted when canonical data is unavailable.</p>
     */
    private String resolveExternalReference(
            List<CanonicalField> keyFields,
            Map<String, Object> payload,
            Map<String, Object> rawRecord) {
        if (!keyFields.isEmpty()) {
            Object value = payload.get(keyFields.get(0).getCanonicalName());
            if (value != null) {
                return value.toString();
            }
        }
        Object candidate = rawRecord.get("externalReference");
        return candidate != null ? candidate.toString() : null;
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise ingestion payload", e);
        }
    }
}
