package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Matching engine that operates on the dynamic configuration and staged
 * source data model.
 */
@Component
public class DynamicMatchingEngine implements MatchingEngine {

    private final DynamicReconciliationContextLoader contextLoader;

    public DynamicMatchingEngine(DynamicReconciliationContextLoader contextLoader) {
        this.contextLoader = contextLoader;
    }

    @Override
    public MatchingResult execute(com.universal.reconciliation.domain.entity.ReconciliationDefinition definition) {
        DynamicReconciliationContext context = contextLoader.load(definition);
        return execute(context);
    }

    private MatchingResult execute(DynamicReconciliationContext context) {
        Map<String, Map<String, Object>> anchorRecords = context.anchor().recordsByKey();
        List<DynamicSourceDataset> otherSources = context.otherSources();
        List<BreakCandidate> breakCandidates = new ArrayList<>();
        int matched = 0;
        int mismatched = 0;
        int missing = 0;

        for (Map.Entry<String, Map<String, Object>> anchorEntry : anchorRecords.entrySet()) {
            String canonicalKey = anchorEntry.getKey();
            Map<String, Object> anchorRecord = anchorEntry.getValue();
            Map<String, Map<String, Object>> sourcesSnapshot = new LinkedHashMap<>();
            sourcesSnapshot.put(context.anchor().source().getCode(), anchorRecord);

            List<String> missingSources = new ArrayList<>();
            boolean differenceDetected = false;

            for (DynamicSourceDataset dataset : otherSources) {
                Map<String, Object> candidate = dataset.recordsByKey().get(canonicalKey);
                if (candidate == null) {
                    missingSources.add(dataset.source().getCode());
                    sourcesSnapshot.put(dataset.source().getCode(), Map.of());
                    differenceDetected = true;
                    continue;
                }

                sourcesSnapshot.put(dataset.source().getCode(), candidate);
                if (!recordsMatch(anchorRecord, candidate, context.compareFields())) {
                    differenceDetected = true;
                }
            }

            if (differenceDetected) {
                BreakType breakType = missingSources.isEmpty() ? BreakType.MISMATCH : BreakType.SOURCE_MISSING;
                if (missingSources.isEmpty()) {
                    mismatched++;
                } else {
                    missing++;
                }
                Map<String, String> classifications = resolveClassifications(sourcesSnapshot, context.classifierFields());
                breakCandidates.add(new BreakCandidate(
                        breakType,
                        immutableSnapshot(sourcesSnapshot),
                        Map.copyOf(classifications),
                        List.copyOf(missingSources)));
            } else {
                matched++;
            }
        }

        // identify records that exist in non-anchor sources but not in the anchor dataset
        Map<String, Map<String, Map<String, Object>>> missingInAnchor = new LinkedHashMap<>();
        for (DynamicSourceDataset dataset : otherSources) {
            for (Map.Entry<String, Map<String, Object>> entry : dataset.recordsByKey().entrySet()) {
                if (!anchorRecords.containsKey(entry.getKey())) {
                    missingInAnchor
                            .computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>())
                            .put(dataset.source().getCode(), entry.getValue());
                }
            }
        }

        for (Map.Entry<String, Map<String, Map<String, Object>>> entry : missingInAnchor.entrySet()) {
            Map<String, Map<String, Object>> sourcesSnapshot = new LinkedHashMap<>(entry.getValue());
            sourcesSnapshot.put(context.anchor().source().getCode(), Map.of());
            Map<String, String> classifications = resolveClassifications(sourcesSnapshot, context.classifierFields());
            List<String> missingSources = List.of(context.anchor().source().getCode());
            missing++;
            breakCandidates.add(new BreakCandidate(
                    BreakType.ANCHOR_MISSING,
                    immutableSnapshot(sourcesSnapshot),
                    Map.copyOf(classifications),
                    missingSources));
        }

        return new MatchingResult(matched, mismatched, missing, breakCandidates);
    }

    private Map<String, Map<String, Object>> immutableSnapshot(Map<String, Map<String, Object>> snapshot) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
    }

    private Map<String, String> resolveClassifications(
            Map<String, Map<String, Object>> sourcesSnapshot, List<CanonicalField> classifierFields) {
        Map<String, String> classifications = new LinkedHashMap<>();
        for (CanonicalField field : classifierFields) {
            Object value = findFirstNonNull(sourcesSnapshot, field.getCanonicalName());
            if (value != null) {
                String key = field.getClassifierTag() != null ? field.getClassifierTag() : canonicalClassifierKey(field.getRole());
                if (key != null) {
                    classifications.put(key, Objects.toString(value, null));
                }
            }
        }
        return classifications;
    }

    private String canonicalClassifierKey(FieldRole role) {
        return switch (role) {
            case PRODUCT -> "product";
            case SUB_PRODUCT -> "subProduct";
            case ENTITY -> "entity";
            default -> null;
        };
    }

    private Object findFirstNonNull(Map<String, Map<String, Object>> sourcesSnapshot, String canonicalName) {
        for (Map<String, Object> payload : sourcesSnapshot.values()) {
            if (payload == null) {
                continue;
            }
            Object value = payload.get(canonicalName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean recordsMatch(
            Map<String, Object> anchorRecord, Map<String, Object> candidate, List<CanonicalField> compareFields) {
        for (CanonicalField field : compareFields) {
            Object left = anchorRecord.get(field.getCanonicalName());
            Object right = candidate.get(field.getCanonicalName());
            if (!compareValues(left, right, field)) {
                return false;
            }
        }
        return true;
    }

    private boolean compareValues(Object left, Object right, CanonicalField field) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        ComparisonLogic logic = field.getComparisonLogic();
        return switch (logic) {
            case EXACT_MATCH -> normalizedEquals(left, right, field.getDataType());
            case CASE_INSENSITIVE -> left.toString().equalsIgnoreCase(right.toString());
            case NUMERIC_THRESHOLD -> compareNumericWithThreshold(left, right, field.getThresholdPercentage());
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
}
