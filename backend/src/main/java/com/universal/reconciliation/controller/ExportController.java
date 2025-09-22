package com.universal.reconciliation.controller;

import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.ExportService;
import com.universal.reconciliation.service.ReconciliationService;
import com.universal.reconciliation.service.SystemActivityService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides Excel exports for reconciliation runs.
 */
@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final ReconciliationService reconciliationService;
    private final ExportService exportService;
    private final UserContext userContext;
    private final SystemActivityService systemActivityService;

    public ExportController(
            ReconciliationService reconciliationService,
            ExportService exportService,
            UserContext userContext,
            SystemActivityService systemActivityService) {
        this.reconciliationService = reconciliationService;
        this.exportService = exportService;
        this.userContext = userContext;
        this.systemActivityService = systemActivityService;
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<byte[]> exportRun(@PathVariable Long runId) {
        var detail = reconciliationService.fetchRunDetail(runId, userContext.getGroups());
        byte[] excel = exportService.exportToExcel(detail);
        systemActivityService.recordEvent(
                SystemEventType.REPORT_EXPORT,
                String.format("Run %d exported by %s", runId, userContext.getUsername()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reconciliation-run-" + runId + ".xlsx")
                .body(excel);
    }
}
