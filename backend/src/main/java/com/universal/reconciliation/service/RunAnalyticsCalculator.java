package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.RunAnalyticsDto;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Derives dashboard analytics from a run's break items.
 */
@Component
public class RunAnalyticsCalculator {

    private static final List<String> BUCKET_ORDER = List.of("<1 day", "1-3 days", "4-7 days", "8+ days");

    public RunAnalyticsDto calculate(ReconciliationRun run, List<BreakItem> breaks) {
        if (run == null) {
            return RunAnalyticsDto.empty();
        }
        Map<String, Long> statusCounts = countByStatus(breaks);
        Map<String, Long> typeCounts = countByType(breaks);
        Map<String, Long> productCounts = countByClassifier(breaks, BreakItem::getProduct, "Unspecified");
        Map<String, Long> entityCounts = countByClassifier(breaks, BreakItem::getEntityName, "Unspecified");
        Map<String, Long> ageBuckets = bucketByAge(breaks);
        int filteredBreakCount = breaks.size();
        int totalBreakCount = run.getMismatchedCount() + run.getMissingCount();
        int matched = run.getMatchedCount();
        return new RunAnalyticsDto(
                statusCounts, typeCounts, productCounts, entityCounts, ageBuckets, filteredBreakCount, totalBreakCount, matched);
    }

    private Map<String, Long> countByStatus(List<BreakItem> breaks) {
        Map<BreakStatus, Long> statusCounts = breaks.stream()
                .collect(Collectors.groupingBy(BreakItem::getStatus, Collectors.counting()));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (BreakStatus status : BreakStatus.values()) {
            long value = statusCounts.getOrDefault(status, 0L);
            if (value > 0) {
                counts.put(status.name(), value);
            }
        }
        return counts;
    }

    private Map<String, Long> countByType(List<BreakItem> breaks) {
        Map<BreakType, Long> typeCounts = breaks.stream()
                .collect(Collectors.groupingBy(BreakItem::getBreakType, Collectors.counting()));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (BreakType type : BreakType.values()) {
            long value = typeCounts.getOrDefault(type, 0L);
            if (value > 0) {
                counts.put(type.name(), value);
            }
        }
        return counts;
    }

    private Map<String, Long> countByClassifier(
            List<BreakItem> breaks, java.util.function.Function<BreakItem, String> classifier, String defaultLabel) {
        Map<String, Long> interim = new LinkedHashMap<>();
        for (BreakItem item : breaks) {
            String key = classifier.apply(item);
            if (key == null || key.isBlank()) {
                key = defaultLabel;
            }
            interim.merge(key, 1L, Long::sum);
        }
        return interim.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<String, Long> bucketByAge(List<BreakItem> breaks) {
        Instant now = Instant.now();
        Map<String, Long> buckets = new LinkedHashMap<>();
        List<BreakItem> openItems = breaks.stream()
                .filter(item -> item.getStatus() == BreakStatus.OPEN || item.getStatus() == BreakStatus.PENDING_APPROVAL)
                .toList();
        for (BreakItem item : openItems) {
            long days = Duration.between(item.getDetectedAt(), now).toDays();
            String label = bucketLabel(days);
            buckets.merge(label, 1L, Long::sum);
        }
        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(this::compareBuckets))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private String bucketLabel(long days) {
        if (days <= 0) {
            return "<1 day";
        } else if (days <= 3) {
            return "1-3 days";
        } else if (days <= 7) {
            return "4-7 days";
        }
        return "8+ days";
    }

    private int compareBuckets(String left, String right) {
        return Integer.compare(BUCKET_ORDER.indexOf(left), BUCKET_ORDER.indexOf(right));
    }
}
