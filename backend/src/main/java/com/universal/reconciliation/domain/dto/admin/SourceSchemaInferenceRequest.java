package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Describes how an uploaded sample file should be parsed when
 * inferring source schema metadata.
 */
public record SourceSchemaInferenceRequest(
        @NotNull TransformationSampleFileType fileType,
        boolean hasHeader,
        String delimiter,
        String sheetName,
        List<String> sheetNames,
        boolean includeAllSheets,
        String recordPath,
        String encoding,
        Integer limit,
        Integer skipRows) {}
