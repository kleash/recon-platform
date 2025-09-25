package com.universal.reconciliation.controller.admin;

import com.universal.reconciliation.domain.dto.admin.AdminIngestionBatchDto;
import com.universal.reconciliation.domain.dto.admin.AdminIngestionRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationDetailDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationPageDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationPatchRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationSchemaDto;
import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.admin.AdminReconciliationService;
import com.universal.reconciliation.service.ingestion.IngestionAdapterRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * Administrative API surface for managing reconciliation metadata.
 */
@RestController
@RequestMapping("/api/admin/reconciliations")
@PreAuthorize("hasRole('RECON_ADMIN')")
public class AdminReconciliationController {

    private final AdminReconciliationService adminReconciliationService;
    private final UserContext userContext;

    public AdminReconciliationController(
            AdminReconciliationService adminReconciliationService, UserContext userContext) {
        this.adminReconciliationService = adminReconciliationService;
        this.userContext = userContext;
    }

    @GetMapping
    public AdminReconciliationPageDto list(
            @RequestParam(value = "status", required = false) ReconciliationLifecycleStatus status,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "updatedAfter", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant updatedAfter,
            @RequestParam(value = "updatedBefore", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant updatedBefore,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return adminReconciliationService.list(status, owner, updatedAfter, updatedBefore, search, page, size);
    }

    @GetMapping("/{id}")
    public AdminReconciliationDetailDto get(@PathVariable Long id) {
        return adminReconciliationService.get(id);
    }

    @PostMapping
    public ResponseEntity<AdminReconciliationDetailDto> create(@Valid @RequestBody AdminReconciliationRequest request) {
        AdminReconciliationDetailDto created =
                adminReconciliationService.create(request, userContext.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public AdminReconciliationDetailDto update(
            @PathVariable Long id, @Valid @RequestBody AdminReconciliationRequest request) {
        return adminReconciliationService.update(id, request, userContext.getUsername());
    }

    @PatchMapping("/{id}")
    public AdminReconciliationDetailDto patch(
            @PathVariable Long id, @RequestBody AdminReconciliationPatchRequest request) {
        return adminReconciliationService.patch(id, request, userContext.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retire(@PathVariable Long id) {
        adminReconciliationService.retire(id, userContext.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/schema")
    public AdminReconciliationSchemaDto exportSchema(@PathVariable Long id) {
        return adminReconciliationService.exportSchema(id);
    }

    @PostMapping(path = "/{id}/sources/{sourceCode}/batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminIngestionBatchDto ingest(
            @PathVariable Long id,
            @PathVariable String sourceCode,
            @Valid @RequestPart("metadata") AdminIngestionRequest metadata,
            @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Batch payload file is required.");
        }
        IngestionAdapterRequest ingestionRequest = new IngestionAdapterRequest(
                asInputStreamSupplier(file), buildIngestionOptions(metadata));
        return adminReconciliationService.ingest(
                id, sourceCode, metadata, ingestionRequest, userContext.getUsername());
    }

    private Supplier<InputStream> asInputStreamSupplier(MultipartFile file) {
        return () -> {
            try {
                return file.getInputStream();
            } catch (IOException ex) {
                throw new UncheckedIOException("Unable to read ingestion payload", ex);
            }
        };
    }

    private Map<String, Object> buildIngestionOptions(AdminIngestionRequest metadata) {
        Map<String, Object> options = metadata.options() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(metadata.options());
        if (metadata.label() != null) {
            options.putIfAbsent("label", metadata.label());
        }
        return options;
    }
}

