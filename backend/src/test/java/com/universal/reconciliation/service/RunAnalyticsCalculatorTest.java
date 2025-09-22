package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.dto.RunAnalyticsDto;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunAnalyticsCalculatorTest {

    private final RunAnalyticsCalculator calculator = new RunAnalyticsCalculator();

    @Test
    void calculate_summarisesBreakInventoryAndBucketsAge() {
        ReconciliationRun run = new ReconciliationRun();
        run.setMatchedCount(5);
        run.setMismatchedCount(2);
        run.setMissingCount(1);

        Instant now = Instant.now();

        BreakItem freshOpen = breakItem(
                BreakStatus.OPEN,
                BreakType.MISMATCH,
                "Payments",
                "US",
                now.minusSeconds(6 * 60 * 60));
        BreakItem agedPending = breakItem(
                BreakStatus.PENDING_APPROVAL,
                BreakType.MISSING_IN_SOURCE_A,
                "Payments",
                "EU",
                now.minusSeconds(5 * 24 * 60 * 60));
        BreakItem closed = breakItem(
                BreakStatus.CLOSED,
                BreakType.MISMATCH,
                null,
                null,
                now.minusSeconds(10 * 24 * 60 * 60));

        RunAnalyticsDto analytics = calculator.calculate(run, List.of(freshOpen, agedPending, closed));

        assertThat(analytics.breaksByStatus())
                .containsEntry(BreakStatus.OPEN.name(), 1L)
                .containsEntry(BreakStatus.PENDING_APPROVAL.name(), 1L)
                .containsEntry(BreakStatus.CLOSED.name(), 1L);
        assertThat(analytics.breaksByType())
                .containsEntry(BreakType.MISMATCH.name(), 2L)
                .containsEntry(BreakType.MISSING_IN_SOURCE_A.name(), 1L);

        assertThat(analytics.breaksByProduct().keySet()).containsExactly("Payments", "Unspecified");
        assertThat(analytics.breaksByEntity().keySet()).containsExactly("EU", "US", "Unspecified");

        assertThat(analytics.openBreaksByAgeBucket().keySet())
                .containsExactly("<1 day", "4-7 days");
        assertThat(analytics.openBreaksByAgeBucket())
                .containsEntry("<1 day", 1L)
                .containsEntry("4-7 days", 1L);

        assertThat(analytics.filteredBreakCount()).isEqualTo(3);
        assertThat(analytics.totalBreakCount()).isEqualTo(3);
        assertThat(analytics.totalMatchedCount()).isEqualTo(5);
    }

    private BreakItem breakItem(
            BreakStatus status, BreakType type, String product, String entity, Instant detectedAt) {
        BreakItem item = new BreakItem();
        item.setStatus(status);
        item.setBreakType(type);
        item.setProduct(product);
        item.setEntityName(entity);
        item.setDetectedAt(detectedAt);
        return item;
    }
}
