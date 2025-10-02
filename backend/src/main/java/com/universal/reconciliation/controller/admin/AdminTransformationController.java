package com.universal.reconciliation.controller.admin;

import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptGenerationResponse;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestRequest;
import com.universal.reconciliation.domain.dto.admin.GroovyScriptTestResponse;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationApplyRequest;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationApplyResponse;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationPreviewResponse;
import com.universal.reconciliation.domain.dto.admin.SourceTransformationPreviewUploadRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationSampleResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationResponse;
import com.universal.reconciliation.service.admin.AdminTransformationService;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import jakarta.validation.Valid;
import java.util.List;
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

    @PostMapping(value = "/plan/preview/upload", consumes = {"multipart/form-data"})
    public SourceTransformationPreviewResponse previewPlanFromFile(
            @Valid @RequestPart("request") SourceTransformationPreviewUploadRequest request,
            @RequestPart("file") MultipartFile file) {
        return transformationService.previewPlan(request, file);
    }

    @PostMapping(value = "/plan/preview/sheets", consumes = {"multipart/form-data"})
    public List<String> listSheetNames(@RequestPart("file") MultipartFile file) {
        return transformationService.listSampleSheetNames(file);
    }

    @PostMapping("/plan/apply")
    public SourceTransformationApplyResponse applyPlan(
            @Valid @RequestBody SourceTransformationApplyRequest request) {
        return transformationService.applyPlan(request);
    }

    @PostMapping("/groovy/generate")
    public GroovyScriptGenerationResponse generateGroovy(
            @Valid @RequestBody GroovyScriptGenerationRequest request) {
        return transformationService.generateGroovyScript(request);
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
