package com.universal.reconciliation.controller;

import com.universal.reconciliation.domain.dto.ReconciliationListItemDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/{id}/run")
    public ResponseEntity<RunDetailDto> triggerRun(
            @PathVariable("id") Long reconciliationId, @Valid @RequestBody(required = false) TriggerRunRequest request) {
        return ResponseEntity.ok(reconciliationService.triggerRun(reconciliationId, userContext.getGroups()));
    }

    @GetMapping("/{id}/runs/latest")
    public ResponseEntity<RunDetailDto> getLatestRun(@PathVariable("id") Long reconciliationId) {
        return ResponseEntity.ok(reconciliationService.fetchLatestRun(reconciliationId, userContext.getGroups()));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunDetailDto> getRun(@PathVariable Long runId) {
        return ResponseEntity.ok(reconciliationService.fetchRunDetail(runId, userContext.getGroups()));
    }
}
