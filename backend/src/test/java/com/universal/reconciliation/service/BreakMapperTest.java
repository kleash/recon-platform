package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakCommentDto;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.entity.BreakComment;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreakMapperTest {

    private final BreakMapper mapper = new BreakMapper(new ObjectMapper());

    @Test
    void toDto_ordersCommentsAndProtectsPayloads() {
        BreakItem item = new BreakItem();
        item.setId(123L);
        item.setBreakType(BreakType.MISMATCH);
        item.setStatus(BreakStatus.OPEN);
        item.setDetectedAt(Instant.parse("2024-05-01T10:15:30Z"));
        item.setClassificationJson("{\"product\":\"Payments\",\"subProduct\":\"Wire\",\"entity\":\"US\"}");
        item.setSourcePayloadJson(
                "{\"CASH\":{\"amount\":100,\"currency\":\"USD\"},\"GL\":{\"amount\":95}}");
        item.setMissingSourcesJson("[\"GL\"]");

        BreakComment earlier = comment(1L, "uid=ops1", "NOTE", "earlier", Instant.parse("2024-05-01T11:00:00Z"));
        BreakComment later = comment(2L, "uid=ops2", "NOTE", "later", Instant.parse("2024-05-01T12:00:00Z"));
        earlier.setBreakItem(item);
        later.setBreakItem(item);
        item.getComments().add(later);
        item.getComments().add(earlier);

        List<BreakStatus> allowedStatusTransitions = new ArrayList<>(List.of(BreakStatus.OPEN, BreakStatus.CLOSED));

        BreakItemDto dto = mapper.toDto(item, allowedStatusTransitions);

        assertThat(dto.allowedStatusTransitions()).containsExactly(BreakStatus.OPEN, BreakStatus.CLOSED);
        allowedStatusTransitions.add(BreakStatus.PENDING_APPROVAL);
        assertThat(dto.allowedStatusTransitions()).containsExactly(BreakStatus.OPEN, BreakStatus.CLOSED);

        assertThat(dto.comments()).extracting(BreakCommentDto::id).containsExactly(1L, 2L);
        assertThat(dto.classifications()).containsEntry("product", "Payments");
        assertThat(dto.sources()).containsKeys("CASH", "GL");
        assertThat(dto.sources().get("CASH")).containsEntry("amount", 100);
        assertThat(dto.missingSources()).containsExactly("GL");
    }

    private BreakComment comment(Long id, String actor, String action, String text, Instant createdAt) {
        BreakComment comment = new BreakComment();
        comment.setId(id);
        comment.setActorDn(actor);
        comment.setAction(action);
        comment.setComment(text);
        comment.setCreatedAt(createdAt);
        return comment;
    }
}
