package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import jakarta.validation.constraints.NotNull;

/**
 * Payload describing how to parse an uploaded sample file and which
 * transformation plan should be applied when previewing results.
 */
public record SourceTransformationPreviewUploadRequest(
        @NotNull TransformationSampleFileType fileType,
        boolean hasHeader,
        String delimiter,
        String sheetName,
        String recordPath,
        String encoding,
        Integer limit,
        SourceTransformationPlan transformationPlan) {}
