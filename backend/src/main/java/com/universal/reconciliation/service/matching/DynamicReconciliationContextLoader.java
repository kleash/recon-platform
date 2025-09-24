package com.universal.reconciliation.service.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import com.universal.reconciliation.domain.entity.SourceDataRecord;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.repository.CanonicalFieldRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SourceDataRecordRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds {@link DynamicReconciliationContext} instances from persisted
 * configuration and staged source data.
 */
@Component
public class DynamicReconciliationContextLoader {

    private static final Logger log = LoggerFactory.getLogger(DynamicReconciliationContextLoader.class);

    private final CanonicalFieldRepository canonicalFieldRepository;
    private final ReconciliationSourceRepository sourceRepository;
    private final SourceDataBatchRepository batchRepository;
    private final SourceDataRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    public DynamicReconciliationContextLoader(
            CanonicalFieldRepository canonicalFieldRepository,
            ReconciliationSourceRepository sourceRepository,
            SourceDataBatchRepository batchRepository,
            SourceDataRecordRepository recordRepository,
            ObjectMapper objectMapper) {
        this.canonicalFieldRepository = canonicalFieldRepository;
        this.sourceRepository = sourceRepository;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DynamicReconciliationContext load(ReconciliationDefinition definition) {
        List<CanonicalField> canonicalFields = canonicalFieldRepository.findByDefinitionOrderByDisplayOrderAsc(definition);
        if (canonicalFields.isEmpty()) {
            throw new IllegalStateException("Reconciliation definition lacks canonical field configuration");
        }

        List<CanonicalField> keyFields = canonicalFields.stream()
                .filter(field -> FieldRole.KEY.equals(field.getRole()))
                .toList();
        if (keyFields.isEmpty()) {
            throw new IllegalStateException("At least one canonical KEY field is required");
        }

        List<CanonicalField> compareFields = canonicalFields.stream()
                .filter(field -> FieldRole.COMPARE.equals(field.getRole()))
                .toList();

        List<CanonicalField> classifierFields = canonicalFields.stream()
                .filter(field -> field.getClassifierTag() != null
                        || FieldRole.PRODUCT.equals(field.getRole())
                        || FieldRole.SUB_PRODUCT.equals(field.getRole())
                        || FieldRole.ENTITY.equals(field.getRole()))
                .sorted(Comparator.comparing(CanonicalField::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        List<ReconciliationSource> sources = sourceRepository.findByDefinition(definition);
        if (sources.isEmpty()) {
            throw new IllegalStateException("Reconciliation definition has no configured sources");
        }

        Optional<ReconciliationSource> anchorSource = sources.stream().filter(ReconciliationSource::isAnchor).findFirst();
        if (anchorSource.isEmpty()) {
            throw new IllegalStateException("Reconciliation definition must designate an anchor source");
        }

        List<DynamicSourceDataset> datasets = new ArrayList<>();
        for (ReconciliationSource source : sources) {
            SourceDataBatch batch = batchRepository
                    .findFirstBySourceOrderByIngestedAtDesc(source)
                    .orElse(null);
            Map<String, Map<String, Object>> records = new LinkedHashMap<>();
            if (batch != null) {
                try (var dataRecords = recordRepository.streamByBatch(batch)) {
                    dataRecords.forEach(record -> {
                        Map<String, Object> payload = parsePayload(record);
                        records.put(record.getCanonicalKey(), payload);
                    });
                }
            } else {
                log.warn(
                        "No data batch found for source {} in definition {}", source.getCode(), definition.getCode());
            }
            datasets.add(new DynamicSourceDataset(source, batch, records));
        }

        DynamicSourceDataset anchorDataset = datasets.stream()
                .filter(dataset -> dataset.source().getId().equals(anchorSource.get().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Anchor dataset could not be resolved"));

        List<DynamicSourceDataset> otherDatasets = datasets.stream()
                .filter(dataset -> !dataset.source().getId().equals(anchorDataset.source().getId()))
                .toList();

        return new DynamicReconciliationContext(
                definition,
                canonicalFields,
                keyFields,
                compareFields,
                classifierFields,
                anchorDataset,
                otherDatasets);
    }

    private Map<String, Object> parsePayload(SourceDataRecord record) {
        try {
            return objectMapper.readValue(record.getPayloadJson(), Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to parse canonical payload for record " + record.getId(), e);
        }
    }
}
