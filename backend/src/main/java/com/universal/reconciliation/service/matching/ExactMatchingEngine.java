package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationField;
import com.universal.reconciliation.domain.entity.SourceRecordA;
import com.universal.reconciliation.domain.entity.SourceRecordB;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
 * Implements the configurable matching algorithm introduced in Phase 2.
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
                RecordSnapshot snapshot = captureRecord(record, metadata);
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
                RecordSnapshot sourceBSnapshot = captureRecord(record, metadata);
                RecordSnapshot sourceASnapshot = sourceAIndex.remove(key);
                if (sourceASnapshot != null) {
                    if (recordsMatch(sourceASnapshot, sourceBSnapshot, metadata.compareRules())) {
                        matched++;
                    } else {
                        mismatched++;
                        breakCandidates.add(new BreakCandidate(
                                BreakType.MISMATCH,
                                sourceASnapshot.values(),
                                sourceBSnapshot.values(),
                                coalesce(sourceASnapshot.product(), sourceBSnapshot.product()),
                                coalesce(sourceASnapshot.subProduct(), sourceBSnapshot.subProduct()),
                                coalesce(sourceASnapshot.entity(), sourceBSnapshot.entity())));
                    }
                } else {
                    missing++;
                    breakCandidates.add(new BreakCandidate(
                            BreakType.MISSING_IN_SOURCE_A,
                            Map.of(),
                            sourceBSnapshot.values(),
                            sourceBSnapshot.product(),
                            sourceBSnapshot.subProduct(),
                            sourceBSnapshot.entity()));
                }
            });
        }

        for (RecordSnapshot remaining : sourceAIndex.values()) {
            missing++;
            breakCandidates.add(new BreakCandidate(
                    BreakType.MISSING_IN_SOURCE_B,
                    remaining.values(),
                    Map.of(),
                    remaining.product(),
                    remaining.subProduct(),
                    remaining.entity()));
        }

        return new MatchingResult(matched, mismatched, missing, breakCandidates);
    }

    private boolean recordsMatch(
            RecordSnapshot sourceA, RecordSnapshot sourceB, List<ComparisonRule> compareRules) {
        for (ComparisonRule rule : compareRules) {
            Object left = sourceA.values().get(rule.field());
            Object right = sourceB.values().get(rule.field());
            if (!compareValues(left, right, rule)) {
                return false;
            }
        }
        return true;
    }

    private boolean compareValues(Object left, Object right, ComparisonRule rule) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        return switch (rule.logic()) {
            case EXACT_MATCH -> normalizedEquals(left, right, rule.dataType());
            case CASE_INSENSITIVE -> left.toString().equalsIgnoreCase(right.toString());
            case NUMERIC_THRESHOLD -> compareNumericWithThreshold(left, right, rule.threshold());
            case DATE_ONLY -> Objects.equals(asLocalDate(left), asLocalDate(right));
        };
    }

    private boolean normalizedEquals(Object left, Object right, FieldDataType dataType) {
        return switch (dataType) {
            case DECIMAL, INTEGER -> toBigDecimal(left).compareTo(toBigDecimal(right)) == 0;
            case DATE -> Objects.equals(asLocalDate(left), asLocalDate(right));
            case STRING -> Objects.equals(left.toString(), right.toString());
        };
    }

    private boolean compareNumericWithThreshold(Object left, Object right, BigDecimal configuredThreshold) {
        BigDecimal leftValue = toBigDecimal(left);
        BigDecimal rightValue = toBigDecimal(right);
        BigDecimal threshold = configuredThreshold != null ? configuredThreshold : BigDecimal.ZERO;
        BigDecimal tolerance = leftValue.abs().multiply(threshold).divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
        return leftValue.subtract(rightValue).abs().compareTo(tolerance) <= 0;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }

    private LocalDate asLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.util.Date legacyDate) {
            return legacyDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (DateTimeParseException ex) {
            throw new IllegalStateException("Unable to parse date value: " + value, ex);
        }
    }

    private String buildKey(Object record, List<String> keyFields) {
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(record);
        return keyFields.stream()
                .map(field -> Objects.toString(accessor.getPropertyValue(field), ""))
                .collect(Collectors.joining("|"));
    }

    private RecordSnapshot captureRecord(Object record, MatchingMetadata metadata) {
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(record);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : metadata.outputFields()) {
            values.put(field, accessor.getPropertyValue(field));
        }
        String product = metadata.productField() != null
                ? Objects.toString(accessor.getPropertyValue(metadata.productField()), null)
                : null;
        String subProduct = metadata.subProductField() != null
                ? Objects.toString(accessor.getPropertyValue(metadata.subProductField()), null)
                : null;
        String entity = metadata.entityField() != null
                ? Objects.toString(accessor.getPropertyValue(metadata.entityField()), null)
                : null;
        return new RecordSnapshot(Collections.unmodifiableMap(values), product, subProduct, entity);
    }

    private String coalesce(String first, String second) {
        return first != null ? first : second;
    }

    private record RecordSnapshot(Map<String, Object> values, String product, String subProduct, String entity) {
    }

    private record ComparisonRule(
            String field, FieldDataType dataType, ComparisonLogic logic, BigDecimal threshold) {
    }

    private record MatchingMetadata(
            List<String> keyFields,
            List<ComparisonRule> compareRules,
            Set<String> outputFields,
            String productField,
            String subProductField,
            String entityField) {

        static MatchingMetadata fromDefinition(ReconciliationDefinition definition) {
            List<ReconciliationField> fields = new ArrayList<>(definition.getFields());
            List<ReconciliationField> keyFields = filterByRole(fields, FieldRole.KEY);
            Assert.state(!keyFields.isEmpty(), "Reconciliation definition must declare at least one KEY field");

            List<ReconciliationField> compareFields = filterByRole(fields, FieldRole.COMPARE);
            List<ComparisonRule> rules = compareFields.stream()
                    .map(field -> new ComparisonRule(
                            field.getSourceField(),
                            field.getDataType(),
                            field.getComparisonLogic(),
                            field.getThresholdPercentage()))
                    .toList();

            Set<String> outputFields = new LinkedHashSet<>();
            keyFields.stream().map(ReconciliationField::getSourceField).forEach(outputFields::add);
            rules.stream().map(ComparisonRule::field).forEach(outputFields::add);
            filterByRole(fields, FieldRole.DISPLAY).stream()
                    .map(ReconciliationField::getSourceField)
                    .forEach(outputFields::add);

            String productField = singleFieldName(fields, FieldRole.PRODUCT);
            String subProductField = singleFieldName(fields, FieldRole.SUB_PRODUCT);
            String entityField = singleFieldName(fields, FieldRole.ENTITY);

            if (productField != null) {
                outputFields.add(productField);
            }
            if (subProductField != null) {
                outputFields.add(subProductField);
            }
            if (entityField != null) {
                outputFields.add(entityField);
            }

            return new MatchingMetadata(
                    keyFields.stream().map(ReconciliationField::getSourceField).toList(),
                    rules,
                    Set.copyOf(outputFields),
                    productField,
                    subProductField,
                    entityField);
        }

        private static List<ReconciliationField> filterByRole(
                Collection<ReconciliationField> fields, FieldRole role) {
            return fields.stream().filter(field -> role.equals(field.getRole())).toList();
        }

        private static String singleFieldName(Collection<ReconciliationField> fields, FieldRole role) {
            List<ReconciliationField> matching = fields.stream()
                    .filter(field -> role.equals(field.getRole()))
                    .toList();
            Assert.state(
                    matching.size() <= 1,
                    () -> "Reconciliation definition must declare at most one " + role + " field");
            return matching.isEmpty() ? null : matching.get(0).getSourceField();
        }
    }
}

