package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.service.search.BreakSearchCriteria;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class BreakSearchCriteriaFactoryTest {

    private BreakSearchCriteriaFactory factory;

    @BeforeEach
    void setUp() {
        factory = new BreakSearchCriteriaFactory();
    }

    @Test
    void fromQueryParamsShouldResolveDatesAndEnums() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("fromDate", "2024-05-01");
        params.add("toDate", "2024-05-03");
        params.add("status", "OPEN");
        params.add("status", "CLOSED");
        params.add("runType", "MANUAL_API");
        params.add("size", "500");

        BreakSearchCriteria criteria = factory.fromQueryParams(params);

        assertThat(criteria.fromDate()).isEqualTo(Instant.parse("2024-04-30T16:00:00Z"));
        assertThat(criteria.toDate()).isEqualTo(Instant.parse("2024-05-03T15:59:59.999999999Z"));
        assertThat(criteria.statuses()).containsExactlyInAnyOrder(BreakStatus.OPEN, BreakStatus.CLOSED);
        assertThat(criteria.triggerTypes()).containsExactly(TriggerType.MANUAL_API);
        assertThat(criteria.pageSize()).isEqualTo(500);
    }

    @Test
    void fromQueryParamsShouldDefaultWhenMissing() {
        BreakSearchCriteria criteria = factory.fromQueryParams(new LinkedMultiValueMap<>());
        assertThat(criteria.fromDate()).isNull();
        assertThat(criteria.pageSize()).isEqualTo(200);
        assertThat(criteria.hasColumnFilters()).isFalse();
    }

    @Test
    void fromQueryParamsRejectsInvalidEnum() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("status", "UNKNOWN");
        assertThatThrownBy(() -> factory.fromQueryParams(params))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void fromQueryParamsShouldParseColumnFiltersAndOperators() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter.product", "FX,MM");
        params.add("operator.product", "contains");

        BreakSearchCriteria criteria = factory.fromQueryParams(params);

        assertThat(criteria.hasColumnFilters()).isTrue();
        assertThat(criteria.columnFilterValues())
                .singleElement()
                .satisfies(filter -> {
                    assertThat(filter.key()).isEqualTo("product");
                    assertThat(filter.operator()).isEqualTo(com.universal.reconciliation.domain.enums.FilterOperator.CONTAINS);
                    assertThat(filter.values()).containsExactly("FX", "MM");
                });
    }

    @Test
    void fromQueryParamsShouldRejectInvalidRunId() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("runId", "abc");

        assertThatThrownBy(() -> factory.fromQueryParams(params))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
