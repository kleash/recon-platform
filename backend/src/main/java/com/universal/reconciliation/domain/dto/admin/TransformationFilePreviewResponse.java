package com.universal.reconciliation.domain.dto.admin;

import java.util.List;
import java.util.Map;

public record TransformationFilePreviewResponse(List<Row> rows) {

    public record Row(
            int rowNumber,
            Map<String, Object> rawRecord,
            Object valueBefore,
            Object transformedValue,
            String error) {}
}
