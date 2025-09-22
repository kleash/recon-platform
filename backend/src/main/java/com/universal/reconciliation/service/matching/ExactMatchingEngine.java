package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationField;
import com.universal.reconciliation.domain.entity.SourceRecordA;
import com.universal.reconciliation.domain.entity.SourceRecordB;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Implements the MVP exact-matching algorithm required for Phase 1.
 */
@Component
public class ExactMatchingEngine implements MatchingEngine {

    private final SourceRecordARepository sourceARepository;
    private final SourceRecordBRepository sourceBRepository;

    public ExactMatchingEngine(
            SourceRecordARepository sourceARepository,
            SourceRecordBRepository sourceBRepository) {
        this.sourceARepository = sourceARepository;
        this.sourceBRepository = sourceBRepository;
    }

    @Override
    public MatchingResult execute(ReconciliationDefinition definition) {
        MatchingMetadata metadata = MatchingMetadata.fromDefinition(definition);

        Map<String, RecordSnapshot> sourceAIndex = new LinkedHashMap<>();
        try (Stream<SourceRecordA> stream = sourceARepository.streamAll()) {
            stream.forEach(record -> {
                String key = buildKey(record, metadata.keyFields());
                RecordSnapshot snapshot = captureRecord(record, metadata.outputFields());
                if (sourceAIndex.putIfAbsent(key, snapshot) != null) {
                    throw new IllegalStateException("Duplicate key detected in source A for " + key);
                }
            });
        }

        int matched = 0;
        int mismatched = 0;
        int missing = 0;
        List<BreakCandidate> breakCandidates = new ArrayList<>();

        try (Stream<SourceRecordB> stream = sourceBRepository.streamAll()) {
            stream.forEach(record -> {
                String key = buildKey(record, metadata.keyFields());
                Map<String, Object> sourceBView = captureRecord(record, metadata.outputFields());
                RecordSnapshot sourceAView = sourceAIndex.remove(key);
                if (sourceAView != null) {
                    if (recordsMatch(sourceAView, sourceBView, metadata.compareFields())) {
                        matched++;
                    } else {
                        mismatched++;
                        breakCandidates.add(new BreakCandidate(BreakType.MISMATCH, sourceAView.values(), sourceBView));
                    }
                } else {
                    missing++;
                    breakCandidates.add(new BreakCandidate(BreakType.MISSING_IN_SOURCE_A, Map.of(), sourceBView));
                }
            });
        }

        for (RecordSnapshot remaining : sourceAIndex.values()) {
            missing++;
            breakCandidates.add(new BreakCandidate(BreakType.MISSING_IN_SOURCE_B, remaining.values(), Map.of()));
        }

        return new MatchingResult(matched, mismatched, missing, breakCandidates);
    }

    private boolean recordsMatch(RecordSnapshot sourceA, Map<String, Object> sourceB, List<String> compareFields) {
        for (String field : compareFields) {
            Object left = sourceA.values().get(field);
            Object right = sourceB.get(field);
            if (!valuesEqual(left, right)) {
                return false;
            }
        }
        return true;
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        if (left instanceof BigDecimal leftDecimal && right instanceof BigDecimal rightDecimal) {
            return leftDecimal.compareTo(rightDecimal) == 0;
        }
        if (left instanceof CharSequence leftText && right instanceof CharSequence rightText) {
            return leftText.toString().equals(rightText.toString());
        }
        return Objects.equals(left, right);
    }

    private String buildKey(Object record, List<String> keyFields) {
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(record);
        return keyFields.stream()
                .map(field -> Objects.toString(accessor.getPropertyValue(field), ""))
                .collect(Collectors.joining("|"));
    }

    private Map<String, Object> captureRecord(Object record, Set<String> fields) {
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(record);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : fields) {
            values.put(field, accessor.getPropertyValue(field));
        }
        return Collections.unmodifiableMap(values);
    }

    private record RecordSnapshot(Map<String, Object> values) {
    }

    private record MatchingMetadata(List<String> keyFields, List<String> compareFields, Set<String> outputFields) {

        static MatchingMetadata fromDefinition(ReconciliationDefinition definition) {
            List<ReconciliationField> fields = new ArrayList<>(definition.getFields());
            List<String> keyFields = extractFields(fields, FieldRole.KEY);
            Assert.state(!keyFields.isEmpty(), "Reconciliation definition must declare at least one KEY field");
            List<String> compareFields = extractFields(fields, FieldRole.COMPARE);
            Set<String> outputFields = new LinkedHashSet<>();
            outputFields.addAll(keyFields);
            outputFields.addAll(compareFields);
            outputFields.addAll(extractFields(fields, FieldRole.DISPLAY));
            return new MatchingMetadata(List.copyOf(keyFields), List.copyOf(compareFields), Set.copyOf(outputFields));
        }

        private static List<String> extractFields(Collection<ReconciliationField> fields, FieldRole role) {
            return fields.stream()
                    .filter(field -> role.equals(field.getRole()))
                    .map(ReconciliationField::getSourceField)
                    .toList();
        }
    }
}
