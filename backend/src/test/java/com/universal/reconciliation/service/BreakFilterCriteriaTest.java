package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.enums.BreakStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BreakFilterCriteriaTest {

    @Test
    void resolvedStatuses_defaultsToAllWhenUnspecified() {
        BreakFilterCriteria criteria = new BreakFilterCriteria(null, null, null, Set.of());

        assertThat(criteria.resolvedStatuses())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(BreakStatus.class));
    }

    @Test
    void matches_appliesDimensionalAndStatusFilters() {
        BreakItem item = new BreakItem();
        item.setProduct("Payments");
        item.setSubProduct("Wire");
        item.setEntityName("US");
        item.setStatus(BreakStatus.OPEN);
        item.setDetectedAt(Instant.now());

        BreakFilterCriteria matching = new BreakFilterCriteria(
                "Payments", "Wire", "US", Set.of(BreakStatus.OPEN));
        BreakFilterCriteria nonMatchingStatus = new BreakFilterCriteria(
                "Payments", "Wire", "US", Set.of(BreakStatus.CLOSED));
        BreakFilterCriteria nonMatchingProduct = new BreakFilterCriteria(
                "FX", null, null, Set.of(BreakStatus.OPEN));

        assertThat(matching.matches(item)).isTrue();
        assertThat(nonMatchingStatus.matches(item)).isFalse();
        assertThat(nonMatchingProduct.matches(item)).isFalse();
    }
}
