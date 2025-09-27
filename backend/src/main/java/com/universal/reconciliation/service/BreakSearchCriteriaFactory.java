package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.BreakColumnFilterDto;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.FilterOperator;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.service.search.BreakSearchCriteria;
import com.universal.reconciliation.service.search.BreakSearchCursor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

/**
 * Translates query parameter maps into {@link BreakSearchCriteria} objects.
 */
@Component
public class BreakSearchCriteriaFactory {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");

    public BreakSearchCriteria fromQueryParams(MultiValueMap<String, String> params) {
        LocalDate fromDate = parseDate(params.getFirst("fromDate"));
        LocalDate toDate = parseDate(params.getFirst("toDate"));
        Instant fromInstant = fromDate != null ? fromDate.atStartOfDay(SGT).toInstant() : null;
        Instant toInstant = toDate != null ? toDate.plusDays(1).atStartOfDay(SGT).minusNanos(1).toInstant() : null;

        Set<Long> runIds = parseLongSet(params.getOrDefault("runId", List.of()));
        Set<TriggerType> triggerTypes = parseEnumSet(params.getOrDefault("runType", List.of()), TriggerType.class, "runType");
        Set<BreakStatus> statuses = parseEnumSet(params.getOrDefault("status", List.of()), BreakStatus.class, "status");

        Map<String, BreakColumnFilterDto> columnFilters = extractColumnFilters(params);
        String search = params.getFirst("search");
        int size = parseInt(params.getFirst("size"), 200);
        boolean includeTotals = Boolean.parseBoolean(params.getFirst("includeTotals"));
        BreakSearchCursor cursor = BreakSearchCursor.fromToken(params.getFirst("cursor"));

        return new BreakSearchCriteria(
                fromInstant,
                toInstant,
                runIds,
                triggerTypes,
                statuses,
                columnFilters,
                search,
                size,
                cursor,
                includeTotals);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Set<Long> parseLongSet(List<String> rawValues) {
        try {
            return rawValues.stream()
                    .flatMap(value -> Stream.of(value.split(",")))
                    .map(String::trim)
                    .filter(str -> !str.isBlank())
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid runId parameter", ex);
        }
    }

    private <E extends Enum<E>> Set<E> parseEnumSet(List<String> rawValues, Class<E> enumType, String parameterName) {
        try {
            return rawValues.stream()
                    .flatMap(value -> Stream.of(value.split(",")))
                    .map(String::trim)
                    .filter(str -> !str.isBlank())
                    .map(str -> Enum.valueOf(enumType, str.toUpperCase()))
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + parameterName + "'", ex);
        }
    }

    private Map<String, BreakColumnFilterDto> extractColumnFilters(MultiValueMap<String, String> params) {
        Map<String, BreakColumnFilterDto> filters = new HashMap<>();
        params.forEach((key, values) -> {
            if (key.startsWith("filter.")) {
                String attribute = key.substring("filter.".length());
                if (attribute.isBlank()) {
                    return;
                }
                List<String> expandedValues = values.stream()
                        .flatMap(value -> Stream.of(value.split(",")))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
                FilterOperator operator = resolveOperator(params, attribute);
                filters.put(attribute, new BreakColumnFilterDto(attribute, operator, expandedValues));
            }
        });
        return filters;
    }

    private FilterOperator resolveOperator(MultiValueMap<String, String> params, String attribute) {
        String operatorKey = "operator." + attribute;
        if (!params.containsKey(operatorKey)) {
            return FilterOperator.EQUALS;
        }
        String raw = params.getFirst(operatorKey);
        if (raw == null) {
            return FilterOperator.EQUALS;
        }
        try {
            return FilterOperator.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid operator for filter '" + attribute + "'", ex);
        }
    }
}

