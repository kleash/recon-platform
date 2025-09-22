package com.universal.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakCommentDto;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.entity.BreakComment;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.enums.BreakStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Converts persistence entities into DTOs consumed by the API layer.
 */
@Component
public class BreakMapper {

    private static final Logger log = LoggerFactory.getLogger(BreakMapper.class);

    private final ObjectMapper objectMapper;

    public BreakMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BreakItemDto toDto(BreakItem item, List<BreakStatus> allowedStatuses) {
        List<BreakCommentDto> comments = item.getComments().stream()
                .sorted(Comparator.comparing(BreakComment::getCreatedAt))
                .map(comment -> new BreakCommentDto(
                        comment.getId(), comment.getActorDn(), comment.getAction(), comment.getComment(), comment.getCreatedAt()))
                .toList();
        return new BreakItemDto(
                item.getId(),
                item.getBreakType(),
                item.getStatus(),
                item.getProduct(),
                item.getSubProduct(),
                item.getEntityName(),
                List.copyOf(allowedStatuses),
                item.getDetectedAt(),
                readJson(item.getId(), "sourceA", item.getSourceAJson()),
                readJson(item.getId(), "sourceB", item.getSourceBJson()),
                comments);
    }

    private Map<String, Object> readJson(Long breakId, String sourceLabel, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error(
                    "Failed to deserialize stored break payload for break {} ({})", breakId, sourceLabel, e);
            return Map.of();
        }
    }
}
