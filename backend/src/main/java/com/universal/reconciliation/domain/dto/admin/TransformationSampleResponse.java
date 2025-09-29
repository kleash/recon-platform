package com.universal.reconciliation.domain.dto.admin;

import java.util.List;
import java.util.Map;

public record TransformationSampleResponse(List<Row> rows) {

    public record Row(
            Long recordId,
            String batchLabel,
            String ingestedAt,
            String canonicalKey,
            String externalReference,
            Map<String, Object> rawRecord,
            Map<String, Object> canonicalPayload) {}
}
