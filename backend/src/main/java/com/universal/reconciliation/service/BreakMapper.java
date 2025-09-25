package com.universal.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakCommentDto;
import com.universal.reconciliation.domain.dto.BreakHistoryEntryDto;
import com.universal.reconciliation.domain.dto.BreakHistoryEntryDto.EntryType;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.entity.BreakComment;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.enums.BreakStatus;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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

        List<BreakHistoryEntryDto> history = Stream.concat(
                        item.getComments().stream().map(comment -> new BreakHistoryEntryDto(
                                EntryType.COMMENT,
                                comment.getActorDn(),
                                null,
                                comment.getAction(),
                                comment.getComment(),
                                null,
                                null,
                                comment.getCreatedAt(),
                                null)),
                        item.getWorkflowAudits().stream().map(audit -> new BreakHistoryEntryDto(
                                EntryType.WORKFLOW,
                                audit.getActorDn(),
                                audit.getActorRole(),
                                audit.getNewStatus().name(),
                                audit.getComment(),
                                audit.getPreviousStatus(),
                                audit.getNewStatus(),
                                audit.getCreatedAt(),
                                audit.getCorrelationId())))
                .sorted(Comparator.comparing(BreakHistoryEntryDto::occurredAt))
                .toList();

        Map<String, String> classifications = readStringMap(item.getId(), "classification", item.getClassificationJson());
        if (classifications == null || classifications.isEmpty()) {
            Map<String, String> fallback = new LinkedHashMap<>();
            if (item.getProduct() != null) {
                fallback.put("product", item.getProduct());
            }
            if (item.getSubProduct() != null) {
                fallback.put("subProduct", item.getSubProduct());
            }
            if (item.getEntityName() != null) {
                fallback.put("entity", item.getEntityName());
            }
            classifications = fallback;
        }

        Map<String, Map<String, Object>> sources = readNestedMap(item.getId(), "sourcePayload", item.getSourcePayloadJson());
        if (sources == null || sources.isEmpty()) {
            Map<String, Map<String, Object>> fallback = new LinkedHashMap<>();
            Map<String, Object> legacyA = readMap(item.getId(), "sourceA", item.getSourceAJson());
            Map<String, Object> legacyB = readMap(item.getId(), "sourceB", item.getSourceBJson());
            if (!legacyA.isEmpty()) {
                fallback.put("SOURCE_A", legacyA);
            }
            if (!legacyB.isEmpty()) {
                fallback.put("SOURCE_B", legacyB);
            }
            sources = fallback;
        }

        List<String> missingSources = readStringList(item.getId(), "missingSources", item.getMissingSourcesJson());
        if (missingSources == null) {
            missingSources = List.of();
        }

        return new BreakItemDto(
                item.getId(),
                item.getBreakType(),
                item.getStatus(),
                classifications,
                List.copyOf(allowedStatuses),
                item.getDetectedAt(),
                sources,
                missingSources,
                comments,
                history,
                item.getSubmittedByDn(),
                item.getSubmittedByGroup(),
                item.getSubmittedAt());
    }

    private Map<String, Object> readMap(Long breakId, String label, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize break payload for break {} ({})", breakId, label, e);
            return Map.of();
        }
    }

    private Map<String, Map<String, Object>> readNestedMap(Long breakId, String label, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize nested break payload for break {} ({})", breakId, label, e);
            return Map.of();
        }
    }

    private Map<String, String> readStringMap(Long breakId, String label, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize classification map for break {} ({})", breakId, label, e);
            return Map.of();
        }
    }

    private List<String> readStringList(Long breakId, String label, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize missing sources for break {} ({})", breakId, label, e);
            return List.of();
        }
    }
}
