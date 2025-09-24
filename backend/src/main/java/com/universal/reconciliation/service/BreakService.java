package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.AddBreakCommentRequest;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.BulkBreakFailureDto;
import com.universal.reconciliation.domain.dto.BulkBreakUpdateRequest;
import com.universal.reconciliation.domain.dto.BulkBreakUpdateResponse;
import com.universal.reconciliation.domain.dto.UpdateBreakStatusRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakComment;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.BreakWorkflowAudit;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.repository.BreakCommentRepository;
import com.universal.reconciliation.repository.BreakItemRepository;
import com.universal.reconciliation.repository.BreakWorkflowAuditRepository;
import com.universal.reconciliation.security.UserContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
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
    private final BreakWorkflowAuditRepository breakWorkflowAuditRepository;

    public BreakService(
            BreakItemRepository breakItemRepository,
            BreakCommentRepository breakCommentRepository,
            UserContext userContext,
            UserDirectoryService userDirectoryService,
            BreakMapper breakMapper,
            BreakAccessService breakAccessService,
            SystemActivityService systemActivityService,
            BreakWorkflowAuditRepository breakWorkflowAuditRepository) {
        this.breakItemRepository = breakItemRepository;
        this.breakCommentRepository = breakCommentRepository;
        this.userContext = userContext;
        this.userDirectoryService = userDirectoryService;
        this.breakMapper = breakMapper;
        this.breakAccessService = breakAccessService;
        this.systemActivityService = systemActivityService;
        this.breakWorkflowAuditRepository = breakWorkflowAuditRepository;
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
        context.breakItem().getComments().add(comment);
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

        String username = userContext.getUsername();
        String actorDn = userDirectoryService.personDn(username);
        Instant now = Instant.now();
        BreakStatus targetStatus = request.status();
        String normalizedComment = normalizeComment(targetStatus, request.comment());
        AccessRole actorRole = resolveActorRole(targetStatus, context);

        enforceSelfApprovalGuard(breakItem, targetStatus, actorRole, actorDn);

        BreakStatus previousStatus = breakItem.getStatus();
        breakItem.setStatus(targetStatus);
        updateSubmissionMetadata(breakItem, targetStatus, actorDn, context.entries(), now);
        breakItemRepository.save(breakItem);

        BreakWorkflowAudit audit = new BreakWorkflowAudit();
        audit.setBreakItem(breakItem);
        audit.setPreviousStatus(previousStatus);
        audit.setNewStatus(targetStatus);
        audit.setActorDn(actorDn);
        audit.setActorRole(actorRole);
        audit.setComment(normalizedComment);
        audit.setCorrelationId(request.correlationId());
        audit.setCreatedAt(now);
        breakItem.getWorkflowAudits().add(audit);
        breakWorkflowAuditRepository.save(audit);

        systemActivityService.recordEvent(
                SystemEventType.BREAK_STATUS_CHANGE,
                String.format(
                        "Break %d transitioned from %s to %s by %s (%s)",
                        breakItem.getId(),
                        previousStatus.name(),
                        targetStatus.name(),
                        username,
                        actorRole.name()));

        return breakMapper.toDto(
                breakItem,
                breakAccessService.allowedStatuses(breakItem, context.definition(), context.entries()));
    }

    @Transactional
    public BulkBreakUpdateResponse bulkUpdate(BulkBreakUpdateRequest request) {
        String username = userContext.getUsername();
        String actorDn = userDirectoryService.personDn(username);
        BulkContext bulkContext = loadBulkContexts(request.breakIds());
        Map<Long, BreakContext> contexts = bulkContext.contexts();
        List<BreakItemDto> successes = new ArrayList<>();
        List<BulkBreakFailureDto> failures = new ArrayList<>();
        List<BreakItem> itemsToSave = new ArrayList<>();
        List<BreakComment> commentsToSave = new ArrayList<>();
        List<BreakWorkflowAudit> auditsToSave = new ArrayList<>();
        int statusChanges = 0;
        int commentsAdded = 0;

        bulkContext.missingIds().forEach(id -> failures.add(new BulkBreakFailureDto(id, "Break not found")));

        for (Long breakId : request.breakIds()) {
            BreakContext context = contexts.get(breakId);
            if (context == null) {
                continue;
            }

            BreakItem breakItem = context.breakItem();

            try {
                if (request.hasComment()) {
                    breakAccessService.assertCanComment(breakItem, context.entries());
                }

                AccessRole actorRole = null;
                String statusComment = null;
                if (request.hasStatusChange()) {
                    breakAccessService.assertTransitionAllowed(
                            breakItem, context.definition(), context.entries(), request.status());
                    actorRole = resolveActorRole(request.status(), context);
                    statusComment = normalizeComment(request.status(), request.comment());
                    enforceSelfApprovalGuard(breakItem, request.status(), actorRole, actorDn);

                    BreakStatus previousStatus = breakItem.getStatus();
                    if (previousStatus != request.status()) {
                        Instant now = Instant.now();
                        breakItem.setStatus(request.status());
                        updateSubmissionMetadata(breakItem, request.status(), actorDn, context.entries(), now);
                        itemsToSave.add(breakItem);
                        statusChanges++;

                        BreakWorkflowAudit audit = new BreakWorkflowAudit();
                        audit.setBreakItem(breakItem);
                        audit.setPreviousStatus(previousStatus);
                        audit.setNewStatus(request.status());
                        audit.setActorDn(actorDn);
                        audit.setActorRole(actorRole);
                        audit.setComment(statusComment);
                        audit.setCorrelationId(request.correlationId());
                        audit.setCreatedAt(now);
                        breakItem.getWorkflowAudits().add(audit);
                        auditsToSave.add(audit);
                    }
                }

                if (request.hasComment()) {
                    BreakComment comment = new BreakComment();
                    comment.setBreakItem(breakItem);
                    comment.setAction(request.resolvedAction());
                    comment.setComment(request.trimmedComment());
                    comment.setCreatedAt(Instant.now());
                    comment.setActorDn(actorDn);
                    breakItem.getComments().add(comment);
                    commentsToSave.add(comment);
                    commentsAdded++;
                }

                successes.add(breakMapper.toDto(
                        breakItem,
                        breakAccessService.allowedStatuses(breakItem, context.definition(), context.entries())));
            } catch (AccessDeniedException | IllegalArgumentException e) {
                failures.add(new BulkBreakFailureDto(breakId, e.getMessage()));
            }
        }

        if (!itemsToSave.isEmpty()) {
            breakItemRepository.saveAll(itemsToSave);
        }
        if (!commentsToSave.isEmpty()) {
            breakCommentRepository.saveAll(commentsToSave);
        }
        if (!auditsToSave.isEmpty()) {
            breakWorkflowAuditRepository.saveAll(auditsToSave);
        }

        systemActivityService.recordEvent(
                SystemEventType.BREAK_BULK_ACTION,
                String.format(
                        "%s applied bulk update to %d breaks (%d status changes, %d comments, %d failures)",
                        username,
                        request.breakIds().size(),
                        statusChanges,
                        commentsAdded,
                        failures.size()));

        return new BulkBreakUpdateResponse(successes, failures);
    }

    private BulkContext loadBulkContexts(List<Long> breakIds) {
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

        return new BulkContext(contexts, missing);
    }

    private BreakContext loadBreakContext(Long id) {
        BreakItem item = breakItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Break not found"));
        ReconciliationDefinition definition = item.getRun().getDefinition();
        List<AccessControlEntry> entries = breakAccessService.findEntries(definition, userContext.getGroups());
        breakAccessService.assertCanView(item, entries);
        return new BreakContext(item, definition, entries);
    }

    private String normalizeComment(BreakStatus status, String comment) {
        String trimmed = comment == null ? null : comment.trim();
        if (requiresComment(status) && (trimmed == null || trimmed.isEmpty())) {
            throw new IllegalArgumentException("Comment is required for status " + status);
        }
        return (trimmed == null || trimmed.isEmpty()) ? null : trimmed;
    }

    private boolean requiresComment(BreakStatus status) {
        return status == BreakStatus.CLOSED || status == BreakStatus.REJECTED;
    }

    private AccessRole resolveActorRole(BreakStatus targetStatus, BreakContext context) {
        List<AccessControlEntry> entries =
                breakAccessService.scopedEntries(context.breakItem(), context.entries());
        boolean maker = hasRole(entries, AccessRole.MAKER);
        boolean checker = hasRole(entries, AccessRole.CHECKER) && !maker;

        if (context.definition().isMakerCheckerEnabled()) {
            if (targetStatus == BreakStatus.CLOSED || targetStatus == BreakStatus.REJECTED) {
                if (!checker) {
                    throw new AccessDeniedException("Checker role required for this action");
                }
                return AccessRole.CHECKER;
            }
            if (!maker) {
                throw new AccessDeniedException("Maker role required for this action");
            }
            return AccessRole.MAKER;
        }

        if (checker) {
            return AccessRole.CHECKER;
        }
        if (maker) {
            return AccessRole.MAKER;
        }
        throw new AccessDeniedException("No maker/checker role available for this action");
    }

    private void enforceSelfApprovalGuard(
            BreakItem breakItem, BreakStatus targetStatus, AccessRole actorRole, String actorDn) {
        if (actorRole == AccessRole.CHECKER
                && requiresComment(targetStatus)
                && breakItem.getSubmittedByDn() != null
                && breakItem.getSubmittedByDn().equals(actorDn)) {
            throw new AccessDeniedException("Makers cannot approve or reject their own submissions");
        }
    }

    private void updateSubmissionMetadata(
            BreakItem breakItem,
            BreakStatus targetStatus,
            String actorDn,
            List<AccessControlEntry> entries,
            Instant now) {
        List<AccessControlEntry> scopedEntries = breakAccessService.scopedEntries(breakItem, entries);
        if (targetStatus == BreakStatus.PENDING_APPROVAL) {
            breakItem.setSubmittedByDn(actorDn);
            breakItem.setSubmittedByGroup(resolveGroupForRole(scopedEntries, AccessRole.MAKER).orElse(null));
            breakItem.setSubmittedAt(now);
        } else if (targetStatus == BreakStatus.OPEN) {
            breakItem.setSubmittedByDn(null);
            breakItem.setSubmittedByGroup(null);
            breakItem.setSubmittedAt(null);
        }
    }

    private boolean hasRole(List<AccessControlEntry> entries, AccessRole role) {
        return entries.stream().anyMatch(entry -> role.equals(entry.getRole()));
    }

    private Optional<String> resolveGroupForRole(List<AccessControlEntry> entries, AccessRole role) {
        return entries.stream()
                .filter(entry -> role.equals(entry.getRole()))
                .map(AccessControlEntry::getLdapGroupDn)
                .findFirst();
    }

    private record BreakContext(
            BreakItem breakItem, ReconciliationDefinition definition, List<AccessControlEntry> entries) {}

    private record BulkContext(Map<Long, BreakContext> contexts, List<Long> missingIds) {}
}

