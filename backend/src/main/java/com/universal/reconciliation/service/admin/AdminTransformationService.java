package com.universal.reconciliation.service.admin;

import com.universal.reconciliation.domain.dto.admin.TransformationPreviewRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationPreviewResponse;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationValidationResponse;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import com.universal.reconciliation.service.transform.DataTransformationService;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminTransformationService {

    private final DataTransformationService transformationService;

    public AdminTransformationService(DataTransformationService transformationService) {
        this.transformationService = transformationService;
    }

    public TransformationValidationResponse validate(@Valid TransformationValidationRequest request) {
        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setType(request.type());
        transformation.setExpression(trimToNull(request.expression()));
        transformation.setConfiguration(trimToNull(request.configuration()));
        try {
            transformationService.validate(transformation);
            return new TransformationValidationResponse(true, "Transformation is valid");
        } catch (TransformationEvaluationException ex) {
            return new TransformationValidationResponse(false, ex.getMessage());
        }
    }

    public TransformationPreviewResponse preview(@Valid TransformationPreviewRequest request) {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setTransformations(new LinkedHashSet<>());
        request.transformations().stream()
                .sorted((a, b) -> {
                    Integer left = a.displayOrder() != null ? a.displayOrder() : Integer.MAX_VALUE;
                    Integer right = b.displayOrder() != null ? b.displayOrder() : Integer.MAX_VALUE;
                    return left.compareTo(right);
                })
                .forEach(transformationDto -> {
                    CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
                    transformation.setMapping(mapping);
                    transformation.setType(transformationDto.type());
                    transformation.setExpression(trimToNull(transformationDto.expression()));
                    transformation.setConfiguration(trimToNull(transformationDto.configuration()));
                    transformation.setDisplayOrder(transformationDto.displayOrder());
                    transformation.setActive(transformationDto.active() == null || transformationDto.active());
                    mapping.getTransformations().add(transformation);
                });
        try {
            Object result = transformationService.applyTransformations(
                    mapping, request.value(), request.rawRecord() == null ? Map.of() : request.rawRecord());
            return new TransformationPreviewResponse(result);
        } catch (TransformationEvaluationException ex) {
            throw ex;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

