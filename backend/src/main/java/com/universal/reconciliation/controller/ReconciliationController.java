package com.universal.reconciliation.controller;

import com.universal.reconciliation.domain.dto.ApprovalQueueDto;
import com.universal.reconciliation.domain.dto.BreakSearchPageInfoDto;
import com.universal.reconciliation.domain.dto.BreakSearchResponseDto;
import com.universal.reconciliation.domain.dto.BreakSearchResultRowDto;
import com.universal.reconciliation.domain.dto.GridColumnDto;
import com.universal.reconciliation.domain.dto.ReconciliationListItemDto;
import com.universal.reconciliation.domain.dto.ReconciliationSummaryDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.dto.BreakSelectionResponseDto;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.ReconciliationService;
import com.universal.reconciliation.service.BreakSearchService;
import com.universal.reconciliation.service.BreakFilterCriteria;
import com.universal.reconciliation.service.BreakSearchCriteriaFactory;
import com.universal.reconciliation.service.BreakSelectionService;
import com.universal.reconciliation.service.search.BreakSearchCriteria;
import com.universal.reconciliation.service.search.BreakSearchResult;
import com.universal.reconciliation.service.search.BreakSearchRow;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;

/**
 * Exposes endpoints for reconciliation discovery and execution.
 */
@RestController
@RequestMapping("/api/reconciliations")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final UserContext userContext;
    private final BreakSearchService breakSearchService;
    private final BreakSearchCriteriaFactory breakSearchCriteriaFactory;
    private final BreakSelectionService breakSelectionService;

    public ReconciliationController(
            ReconciliationService reconciliationService,
            UserContext userContext,
            BreakSearchService breakSearchService,
            BreakSearchCriteriaFactory breakSearchCriteriaFactory,
            BreakSelectionService breakSelectionService) {
        this.reconciliationService = reconciliationService;
        this.userContext = userContext;
        this.breakSearchService = breakSearchService;
        this.breakSearchCriteriaFactory = breakSearchCriteriaFactory;
        this.breakSelectionService = breakSelectionService;
    }

    @GetMapping
    public ResponseEntity<List<ReconciliationListItemDto>> listReconciliations() {
        return ResponseEntity.ok(reconciliationService.listAccessible(userContext.getGroups()));
    }

    @GetMapping("/{id}/runs")
    public ResponseEntity<List<ReconciliationSummaryDto>> listRuns(
            @PathVariable("id") Long reconciliationId,
            @RequestParam(value = "limit", required = false, defaultValue = "5") int limit) {
        int boundedLimit = limit <= 0 ? 5 : Math.min(limit, 50);
        return ResponseEntity.ok(
                reconciliationService.listRuns(reconciliationId, userContext.getGroups(), boundedLimit));
    }

    @GetMapping("/{id}/approvals")
    public ResponseEntity<ApprovalQueueDto> approvals(@PathVariable("id") Long reconciliationId) {
        return ResponseEntity.ok(reconciliationService.fetchApprovalQueue(reconciliationId, userContext.getGroups()));
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<RunDetailDto> triggerRun(
            @PathVariable("id") Long reconciliationId, @Valid @RequestBody(required = false) TriggerRunRequest request) {
        TriggerRunRequest effectiveRequest =
                request != null ? request : new TriggerRunRequest(null, null, null, null);
        return ResponseEntity.ok(reconciliationService.triggerRun(
                reconciliationId, userContext.getGroups(), userContext.getUsername(), effectiveRequest));
    }

    @GetMapping("/{id}/runs/latest")
    public ResponseEntity<RunDetailDto> getLatestRun(
            @PathVariable("id") Long reconciliationId,
            @RequestParam(value = "product", required = false) String product,
            @RequestParam(value = "subProduct", required = false) String subProduct,
            @RequestParam(value = "entity", required = false) String entity,
            @RequestParam(value = "status", required = false) List<BreakStatus> statuses) {
        BreakFilterCriteria filter = buildFilter(product, subProduct, entity, statuses);
        return ResponseEntity.ok(
                reconciliationService.fetchLatestRun(reconciliationId, userContext.getGroups(), filter));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunDetailDto> getRun(
            @PathVariable Long runId,
            @RequestParam(value = "product", required = false) String product,
            @RequestParam(value = "subProduct", required = false) String subProduct,
            @RequestParam(value = "entity", required = false) String entity,
            @RequestParam(value = "status", required = false) List<BreakStatus> statuses) {
        BreakFilterCriteria filter = buildFilter(product, subProduct, entity, statuses);
        return ResponseEntity.ok(reconciliationService.fetchRunDetail(runId, userContext.getGroups(), filter));
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<BreakSearchResponseDto> searchResults(
            @PathVariable("id") Long reconciliationId,
            @RequestParam MultiValueMap<String, String> params) {

        BreakSearchCriteria criteria = breakSearchCriteriaFactory.fromQueryParams(params);
        BreakSearchResult result = breakSearchService.search(reconciliationId, criteria, userContext.getGroups());

        List<BreakSearchResultRowDto> rows = result.rows().stream()
                .map(this::toRowDto)
                .toList();
        BreakSearchPageInfoDto pageInfo = new BreakSearchPageInfoDto(
                result.nextCursor() != null ? result.nextCursor().toToken() : null,
                result.hasMore(),
                result.totalCount());
        List<GridColumnDto> columns = result.columns();
        return ResponseEntity.ok(new BreakSearchResponseDto(rows, pageInfo, columns));
    }

    @GetMapping("/{id}/results/ids")
    public ResponseEntity<BreakSelectionResponseDto> selectResultIds(
            @PathVariable("id") Long reconciliationId,
            @RequestParam MultiValueMap<String, String> params) {
        BreakSearchCriteria criteria = breakSearchCriteriaFactory.fromQueryParams(params);
        BreakSelectionService.BreakSelectionResult selection =
                breakSelectionService.collectBreakIds(reconciliationId, criteria, userContext.getGroups());
        return ResponseEntity.ok(new BreakSelectionResponseDto(selection.breakIds(), selection.totalCount()));
    }

    private BreakFilterCriteria buildFilter(
            String product, String subProduct, String entity, List<BreakStatus> statuses) {
        Set<BreakStatus> statusSet = statuses == null ? Set.of() : statuses.stream().collect(Collectors.toSet());
        return new BreakFilterCriteria(product, subProduct, entity, statusSet);
    }

    private BreakSearchResultRowDto toRowDto(BreakSearchRow row) {
        return new BreakSearchResultRowDto(
                row.breakId(),
                row.runId(),
                row.runDateTime(),
                row.runDateTimeZone(),
                row.triggerType(),
                row.breakItem(),
                row.attributeValues());
    }
}
