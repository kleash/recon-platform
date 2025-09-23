package com.universal.reconciliation.controller;

import com.universal.reconciliation.domain.dto.ReconciliationListItemDto;
import com.universal.reconciliation.domain.dto.ReconciliationSummaryDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.ReconciliationService;
import com.universal.reconciliation.service.BreakFilterCriteria;
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

/**
 * Exposes endpoints for reconciliation discovery and execution.
 */
@RestController
@RequestMapping("/api/reconciliations")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final UserContext userContext;

    public ReconciliationController(ReconciliationService reconciliationService, UserContext userContext) {
        this.reconciliationService = reconciliationService;
        this.userContext = userContext;
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

    private BreakFilterCriteria buildFilter(
            String product, String subProduct, String entity, List<BreakStatus> statuses) {
        Set<BreakStatus> statusSet = statuses == null ? Set.of() : statuses.stream().collect(Collectors.toSet());
        return new BreakFilterCriteria(product, subProduct, entity, statusSet);
    }
}
