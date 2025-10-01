package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Applies a transformation plan to an arbitrary in-memory dataset.
 */
public record SourceTransformationApplyRequest(
        SourceTransformationPlan transformationPlan,
        @NotNull List<Map<String, Object>> rows) {}
