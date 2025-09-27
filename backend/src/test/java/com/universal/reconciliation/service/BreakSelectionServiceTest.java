package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.service.search.BreakSearchCriteria;
import com.universal.reconciliation.service.search.BreakSearchCursor;
import com.universal.reconciliation.service.search.BreakSearchResult;
import com.universal.reconciliation.service.search.BreakSearchRow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BreakSelectionServiceTest {

    @Mock
    private BreakSearchService breakSearchService;

    @InjectMocks
    private BreakSelectionService selectionService;

    @Test
    void collectBreakIdsShouldIterateOverPages() {
        BreakSearchCriteria criteria = new BreakSearchCriteria(
                null,
                null,
                java.util.Set.of(),
                java.util.Set.of(),
                java.util.Set.of(BreakStatus.OPEN),
                Map.of(),
                null,
                200,
                null,
                true);

        BreakItemDto dto = new BreakItemDto(
                1L,
                BreakType.MISMATCH,
                BreakStatus.OPEN,
                Map.of(),
                List.of(),
                Instant.now(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);

        BreakSearchRow firstRow = new BreakSearchRow(1L, 10L, Instant.now(), "Asia/Singapore", TriggerType.MANUAL_API, dto, Map.of());
        BreakSearchRow secondRow = new BreakSearchRow(2L, 11L, Instant.now(), "Asia/Singapore", TriggerType.MANUAL_API, dto, Map.of());
        BreakSearchRow thirdRow = new BreakSearchRow(3L, 12L, Instant.now(), "Asia/Singapore", TriggerType.MANUAL_API, dto, Map.of());

        BreakSearchCursor nextCursor = new BreakSearchCursor(Instant.now(), 2L);
        BreakSearchResult firstPage = new BreakSearchResult(List.of(firstRow, secondRow), nextCursor, true, 42L, List.of());
        BreakSearchResult secondPage = new BreakSearchResult(List.of(thirdRow), null, false, -1L, List.of());

        when(breakSearchService.search(eq(99L), any(), eq(List.of("grp"))))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        BreakSelectionService.BreakSelectionResult result =
                selectionService.collectBreakIds(99L, criteria, List.of("grp"));

        assertThat(result.breakIds()).containsExactly(1L, 2L, 3L);
        assertThat(result.totalCount()).isEqualTo(42L);

        ArgumentCaptor<BreakSearchCriteria> captor = ArgumentCaptor.forClass(BreakSearchCriteria.class);
        verify(breakSearchService, times(2)).search(eq(99L), captor.capture(), eq(List.of("grp")));

        BreakSearchCriteria firstInvocation = captor.getAllValues().get(0);
        BreakSearchCriteria secondInvocation = captor.getAllValues().get(1);
        assertThat(firstInvocation.pageSize()).isEqualTo(500);
        assertThat(firstInvocation.cursor()).isNull();
        assertThat(secondInvocation.cursor()).isEqualTo(nextCursor);
    }
}

