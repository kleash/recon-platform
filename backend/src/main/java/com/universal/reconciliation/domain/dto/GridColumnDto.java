package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.FilterOperator;
import java.util.List;

/**
 * Describes a column rendered on the analyst result grid. Includes label,
 * data type hints, and supported filter operators.
 */
public record GridColumnDto(
        String key,
        String label,
        String dataType,
        List<FilterOperator> operators,
        boolean sortable,
        boolean pinnable) {}

