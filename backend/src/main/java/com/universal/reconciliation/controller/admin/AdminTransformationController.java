package com.universal.reconciliation.controller.admin;

import com.universal.reconciliation.domain.dto.admin.TransformationPreviewRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationPreviewResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationResponse;
import com.universal.reconciliation.service.admin.AdminTransformationService;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/transformations")
@PreAuthorize("hasRole('RECON_ADMIN')")
public class AdminTransformationController {

    private final AdminTransformationService transformationService;

    public AdminTransformationController(AdminTransformationService transformationService) {
        this.transformationService = transformationService;
    }

    @PostMapping("/validate")
    public TransformationValidationResponse validate(
            @Valid @RequestBody TransformationValidationRequest request) {
        return transformationService.validate(request);
    }

    @PostMapping("/preview")
    public TransformationPreviewResponse preview(
            @Valid @RequestBody TransformationPreviewRequest request) {
        return transformationService.preview(request);
    }

    @ExceptionHandler(TransformationEvaluationException.class)
    public ResponseEntity<String> handleEvaluationException(TransformationEvaluationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}

