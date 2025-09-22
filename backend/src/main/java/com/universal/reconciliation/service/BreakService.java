package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.AddBreakCommentRequest;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.UpdateBreakStatusRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakComment;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.repository.BreakCommentRepository;
import com.universal.reconciliation.repository.BreakItemRepository;
import com.universal.reconciliation.security.UserContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages user-driven actions on reconciliation breaks.
 */
@Service
public class BreakService {

    private final BreakItemRepository breakItemRepository;
    private final BreakCommentRepository breakCommentRepository;
    private final UserContext userContext;
    private final UserDirectoryService userDirectoryService;
    private final BreakMapper breakMapper;
    private final BreakAccessService breakAccessService;
    private final SystemActivityService systemActivityService;

    public BreakService(
            BreakItemRepository breakItemRepository,
            BreakCommentRepository breakCommentRepository,
            UserContext userContext,
            UserDirectoryService userDirectoryService,
            BreakMapper breakMapper,
            BreakAccessService breakAccessService,
            SystemActivityService systemActivityService) {
        this.breakItemRepository = breakItemRepository;
        this.breakCommentRepository = breakCommentRepository;
        this.userContext = userContext;
        this.userDirectoryService = userDirectoryService;
        this.breakMapper = breakMapper;
        this.breakAccessService = breakAccessService;
        this.systemActivityService = systemActivityService;
    }

    @Transactional
    public BreakItemDto addComment(Long breakId, AddBreakCommentRequest request) {
        BreakContext context = loadBreakContext(breakId);
        breakAccessService.assertCanComment(context.breakItem(), context.entries());

        BreakComment comment = new BreakComment();
        comment.setBreakItem(context.breakItem());
        comment.setAction(request.action());
        comment.setComment(request.comment());
        comment.setCreatedAt(Instant.now());
        comment.setActorDn(userDirectoryService.personDn(userContext.getUsername()));
        breakCommentRepository.save(comment);

        systemActivityService.recordEvent(
                SystemEventType.BREAK_COMMENT,
                String.format(
                        "Comment added to break %d by %s",
                        context.breakItem().getId(), userContext.getUsername()));

        return breakMapper.toDto(
                context.breakItem(),
                breakAccessService.allowedStatuses(context.breakItem(), context.definition(), context.entries()));
    }

    @Transactional
    public BreakItemDto updateStatus(Long breakId, UpdateBreakStatusRequest request) {
        BreakContext context = loadBreakContext(breakId);
        BreakItem breakItem = context.breakItem();

        breakAccessService.assertTransitionAllowed(
                breakItem, context.definition(), context.entries(), request.status());

        BreakStatus previousStatus = breakItem.getStatus();
        breakItem.setStatus(request.status());
        breakItemRepository.save(breakItem);

        BreakComment auditTrail = new BreakComment();
        auditTrail.setBreakItem(breakItem);
        auditTrail.setAction("STATUS_CHANGE");
        auditTrail.setComment("Status updated to " + request.status().name());
        auditTrail.setCreatedAt(Instant.now());
        auditTrail.setActorDn(userDirectoryService.personDn(userContext.getUsername()));
        breakCommentRepository.save(auditTrail);

        systemActivityService.recordEvent(
                SystemEventType.BREAK_STATUS_CHANGE,
                String.format(
                        "Break %d transitioned from %s to %s by %s",
                        breakItem.getId(),
                        previousStatus.name(),
                        request.status().name(),
                        userContext.getUsername()));

        return breakMapper.toDto(
                breakItem,
                breakAccessService.allowedStatuses(breakItem, context.definition(), context.entries()));
    }

    private BreakContext loadBreakContext(Long id) {
        BreakItem item = breakItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Break not found"));
        ReconciliationDefinition definition = item.getRun().getDefinition();
        List<AccessControlEntry> entries = breakAccessService.findEntries(definition, userContext.getGroups());
        breakAccessService.assertCanView(item, entries);
        return new BreakContext(item, definition, entries);
    }

    private record BreakContext(
            BreakItem breakItem, ReconciliationDefinition definition, List<AccessControlEntry> entries) {}
}

