package com.universal.reconciliation.service.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.transform.SourceTransformationPlan;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Serialises and deserialises {@link SourceTransformationPlan} definitions to
 * the JSON payload stored against each {@code ReconciliationSource}.
 */
@Component
public class SourceTransformationPlanMapper {

    private final ObjectMapper objectMapper;

    public SourceTransformationPlanMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<SourceTransformationPlan> deserialize(String json) {
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            SourceTransformationPlan plan = objectMapper.readValue(json, SourceTransformationPlan.class);
            normalise(plan);
            return Optional.of(plan);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid transformation plan JSON", ex);
        }
    }

    public String serialize(SourceTransformationPlan plan) {
        if (plan == null) {
            return null;
        }
        normalise(plan);
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialise transformation plan", ex);
        }
    }

    private void normalise(SourceTransformationPlan plan) {
        if (plan == null) {
            return;
        }
        if (plan.getRowOperations() == null) {
            plan.setRowOperations(new ArrayList<>());
        }
        if (plan.getColumnOperations() == null) {
            plan.setColumnOperations(new ArrayList<>());
        }
        plan.getRowOperations().removeIf(Objects::isNull);
        plan.getColumnOperations().removeIf(Objects::isNull);
    }
}
