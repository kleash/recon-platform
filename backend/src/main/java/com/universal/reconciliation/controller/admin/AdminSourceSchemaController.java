package com.universal.reconciliation.controller.admin;

import com.universal.reconciliation.domain.dto.admin.SourceSchemaInferenceRequest;
import com.universal.reconciliation.domain.dto.admin.SourceSchemaInferenceResponse;
import com.universal.reconciliation.service.admin.AdminSourceSchemaService;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API surface for inferring source schemas from uploaded samples.
 */
@RestController
@RequestMapping("/api/admin/source-schemas")
@PreAuthorize("hasRole('RECON_ADMIN')")
public class AdminSourceSchemaController {

    private final AdminSourceSchemaService schemaService;

    public AdminSourceSchemaController(AdminSourceSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @PostMapping(value = "/infer", consumes = {"multipart/form-data"})
    public SourceSchemaInferenceResponse inferSchema(
            @Valid @RequestPart("request") SourceSchemaInferenceRequest request,
            @RequestPart("file") MultipartFile file) {
        return schemaService.inferSchema(request, file);
    }

    @ExceptionHandler(TransformationEvaluationException.class)
    public ResponseEntity<String> handleEvaluationException(TransformationEvaluationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
