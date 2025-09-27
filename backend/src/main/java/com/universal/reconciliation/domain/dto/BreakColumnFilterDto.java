package com.universal.reconciliation.domain.dto;

import com.universal.reconciliation.domain.enums.FilterOperator;
import java.util.List;

/**
 * Captures a dynamic column filter (key + operator + values) request for the
 * reconciliation result grid.
 */
public record BreakColumnFilterDto(String key, FilterOperator operator, List<String> values) {}

