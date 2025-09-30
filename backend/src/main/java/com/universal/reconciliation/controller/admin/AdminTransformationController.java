package com.universal.reconciliation.controller.admin;

import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationFilePreviewResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationFilePreviewUploadRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationPreviewRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationPreviewResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationSampleResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationResponse;
import com.universal.reconciliation.service.admin.AdminTransformationService;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/preview/upload", consumes = {"multipart/form-data"})
    public TransformationFilePreviewResponse previewFromFile(
            @Valid @RequestPart("request") TransformationFilePreviewUploadRequest request,
            @RequestPart("file") MultipartFile file) {
        return transformationService.previewFromSampleFile(request, file);
    }

    @PostMapping("/groovy/test")
    public GroovyScriptTestResponse testGroovy(
            @Valid @RequestBody GroovyScriptTestRequest request) {
        return transformationService.testGroovyScript(request);
    }

    @GetMapping("/samples")
    public TransformationSampleResponse samples(
            @RequestParam("definitionId") long definitionId,
            @RequestParam("sourceCode") String sourceCode,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return transformationService.loadSampleRows(definitionId, sourceCode, limit);
    }

    @ExceptionHandler(TransformationEvaluationException.class)
    public ResponseEntity<String> handleEvaluationException(TransformationEvaluationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
