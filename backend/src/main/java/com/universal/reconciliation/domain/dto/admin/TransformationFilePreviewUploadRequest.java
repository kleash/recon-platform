package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TransformationFilePreviewUploadRequest(
        @NotNull TransformationSampleFileType fileType,
        boolean hasHeader,
        String delimiter,
        String sheetName,
        String recordPath,
        String valueColumn,
        String encoding,
        Integer limit,
        @NotNull @NotEmpty @Valid List<TransformationPreviewRequest.PreviewTransformationDto> transformations) {}
