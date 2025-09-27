package com.universal.reconciliation.controller;

import com.universal.reconciliation.domain.dto.ExportJobDto;
import com.universal.reconciliation.domain.dto.ExportJobRequestDto;
import com.universal.reconciliation.domain.entity.ExportJob;
import com.universal.reconciliation.domain.enums.ExportFormat;
import com.universal.reconciliation.domain.enums.ExportJobStatus;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.ExportJobService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints for managing asynchronous export jobs.
 */
@RestController
@RequestMapping("/api")
public class ExportJobController {

    private final ExportJobService exportJobService;
    private final UserContext userContext;

    public ExportJobController(ExportJobService exportJobService, UserContext userContext) {
        this.exportJobService = exportJobService;
        this.userContext = userContext;
    }

    @GetMapping("/reconciliations/{id}/export-jobs")
    public ResponseEntity<List<ExportJobDto>> history(@PathVariable("id") Long reconciliationId) {
        return ResponseEntity.ok(exportJobService.listJobs(reconciliationId, userContext.getUsername()));
    }

    @PostMapping("/reconciliations/{id}/export-jobs")
    public ResponseEntity<ExportJobDto> enqueue(
            @PathVariable("id") Long reconciliationId, @Valid @RequestBody ExportJobRequestDto request) {
        ExportJobDto dto = exportJobService.queueDatasetExport(
                reconciliationId, request, userContext.getUsername(), userContext.getGroups());
        return ResponseEntity.accepted().body(dto);
    }

    @GetMapping("/export-jobs/{jobId}")
    public ResponseEntity<ExportJobDto> status(@PathVariable Long jobId) {
        ExportJob job = findJobOrThrow(jobId);
        return ResponseEntity.ok(exportJobService.toDto(job));
    }

    @GetMapping("/export-jobs/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long jobId) {
        ExportJob job = findJobOrThrow(jobId);
        if (job.getStatus() != ExportJobStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Export job is not yet complete");
        }
        if (job.getPayload() == null || job.getPayload().length == 0) {
            throw new ResponseStatusException(HttpStatus.GONE, "Export payload is no longer available");
        }
        MediaType mediaType = mapContentType(job.getFormat());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + job.getFileName())
                .body(job.getPayload());
    }

    private ExportJob findJobOrThrow(Long jobId) {
        return exportJobService
                .findJob(jobId, userContext.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private MediaType mapContentType(ExportFormat format) {
        return switch (format) {
            case CSV -> MediaType.parseMediaType("text/csv");
            case JSONL -> MediaType.parseMediaType("application/x-ndjson");
            case XLSX -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> MediaType.APPLICATION_PDF;
        };
    }
}
