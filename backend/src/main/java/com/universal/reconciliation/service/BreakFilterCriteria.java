package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.enums.BreakStatus;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates filter selections applied to break inventories.
 */
public record BreakFilterCriteria(
        String product, String subProduct, String entity, Set<BreakStatus> statuses) {

    public static BreakFilterCriteria none() {
        return new BreakFilterCriteria(null, null, null, Collections.emptySet());
    }

    public Set<BreakStatus> resolvedStatuses() {
        if (statuses == null || statuses.isEmpty()) {
            return EnumSet.allOf(BreakStatus.class);
        }
        return EnumSet.copyOf(statuses);
    }

    public boolean matches(BreakItem item) {
        if (product != null && !Objects.equals(product, item.getProduct())) {
            return false;
        }
        if (subProduct != null && !Objects.equals(subProduct, item.getSubProduct())) {
            return false;
        }
        if (entity != null && !Objects.equals(entity, item.getEntityName())) {
            return false;
        }
        return resolvedStatuses().contains(item.getStatus());
    }
}

