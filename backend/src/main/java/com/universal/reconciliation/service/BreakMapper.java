package com.universal.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakCommentDto;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.repository.BreakCommentRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converts persistence entities into DTOs consumed by the API layer.
 */
@Component
public class BreakMapper {

    private final BreakCommentRepository breakCommentRepository;
    private final ObjectMapper objectMapper;

    public BreakMapper(BreakCommentRepository breakCommentRepository, ObjectMapper objectMapper) {
        this.breakCommentRepository = breakCommentRepository;
        this.objectMapper = objectMapper;
    }

    public BreakItemDto toDto(BreakItem item) {
        List<BreakCommentDto> comments = breakCommentRepository.findByBreakItemOrderByCreatedAtAsc(item).stream()
                .map(comment -> new BreakCommentDto(
                        comment.getId(), comment.getActorDn(), comment.getAction(), comment.getComment(), comment.getCreatedAt()))
                .toList();
        return new BreakItemDto(
                item.getId(),
                item.getBreakType(),
                item.getStatus(),
                item.getDetectedAt(),
                readJson(item.getSourceAJson()),
                readJson(item.getSourceBJson()),
                comments);
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
