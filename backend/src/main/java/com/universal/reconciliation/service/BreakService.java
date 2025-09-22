package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.AddBreakCommentRequest;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.BulkBreakUpdateRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Transactional
    public List<BreakItemDto> bulkUpdate(BulkBreakUpdateRequest request) {
        String username = userContext.getUsername();
        String actorDn = userDirectoryService.personDn(username);
        Map<Long, BreakContext> contexts = loadBulkContexts(request.breakIds());
        List<BreakItemDto> responses = new ArrayList<>();
        List<BreakItem> itemsToSave = new ArrayList<>();
        List<BreakComment> commentsToSave = new ArrayList<>();
        int statusChanges = 0;
        int commentsAdded = 0;
        String trimmedComment = request.hasComment() ? request.comment().trim() : null;

        for (Long breakId : request.breakIds()) {
            BreakContext context = contexts.get(breakId);
            if (context == null) {
                throw new IllegalArgumentException("Break not found: " + breakId);
            }
            BreakItem breakItem = context.breakItem();

            if (request.hasStatusChange()) {
                breakAccessService.assertTransitionAllowed(
                        breakItem, context.definition(), context.entries(), request.status());
                BreakStatus previousStatus = breakItem.getStatus();
                if (previousStatus != request.status()) {
                    breakItem.setStatus(request.status());
                    itemsToSave.add(breakItem);
                    statusChanges++;

                    BreakComment auditTrail = new BreakComment();
                    auditTrail.setBreakItem(breakItem);
                    auditTrail.setAction("BULK_STATUS_CHANGE");
                    auditTrail.setComment("Status updated to " + request.status().name());
                    auditTrail.setCreatedAt(Instant.now());
                    auditTrail.setActorDn(actorDn);
                    commentsToSave.add(auditTrail);
                }
            }

            if (request.hasComment()) {
                breakAccessService.assertCanComment(breakItem, context.entries());
                BreakComment comment = new BreakComment();
                comment.setBreakItem(breakItem);
                comment.setAction(request.resolvedAction());
                comment.setComment(trimmedComment);
                comment.setCreatedAt(Instant.now());
                comment.setActorDn(actorDn);
                commentsToSave.add(comment);
                commentsAdded++;
            }

            responses.add(breakMapper.toDto(
                    breakItem,
                    breakAccessService.allowedStatuses(breakItem, context.definition(), context.entries())));
        }

        if (!itemsToSave.isEmpty()) {
            breakItemRepository.saveAll(itemsToSave);
        }
        if (!commentsToSave.isEmpty()) {
            breakCommentRepository.saveAll(commentsToSave);
        }

        systemActivityService.recordEvent(
                SystemEventType.BREAK_BULK_ACTION,
                String.format(
                        "%s applied bulk update to %d breaks (%d status changes, %d comments)",
                        username,
                        request.breakIds().size(),
                        statusChanges,
                        commentsAdded));

        return responses;
    }

    private Map<Long, BreakContext> loadBulkContexts(List<Long> breakIds) {
        List<BreakItem> items = breakItemRepository.findAllById(breakIds);
        Map<Long, BreakItem> itemsById = new HashMap<>();
        for (BreakItem item : items) {
            itemsById.put(item.getId(), item);
        }

        List<Long> missing = new ArrayList<>();
        for (Long breakId : breakIds) {
            if (!itemsById.containsKey(breakId)) {
                missing.add(breakId);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Break not found: " + missing);
        }

        Map<Long, BreakContext> contexts = new HashMap<>();
        Map<Long, List<AccessControlEntry>> entriesByDefinition = new HashMap<>();
        List<String> groups = userContext.getGroups();

        for (BreakItem item : items) {
            ReconciliationDefinition definition = item.getRun().getDefinition();
            Long definitionId = definition.getId();
            List<AccessControlEntry> entries = entriesByDefinition.computeIfAbsent(
                    definitionId,
                    id -> breakAccessService.findEntries(definition, groups));
            breakAccessService.assertCanView(item, entries);
            contexts.put(item.getId(), new BreakContext(item, definition, entries));
        }

        return contexts;
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

