package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.AddBreakCommentRequest;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.UpdateBreakStatusRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakComment;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
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
    private final AccessControlEntryRepository accessControlEntryRepository;
    private final UserContext userContext;
    private final UserDirectoryService userDirectoryService;
    private final BreakMapper breakMapper;

    public BreakService(
            BreakItemRepository breakItemRepository,
            BreakCommentRepository breakCommentRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            UserContext userContext,
            UserDirectoryService userDirectoryService,
            BreakMapper breakMapper) {
        this.breakItemRepository = breakItemRepository;
        this.breakCommentRepository = breakCommentRepository;
        this.accessControlEntryRepository = accessControlEntryRepository;
        this.userContext = userContext;
        this.userDirectoryService = userDirectoryService;
        this.breakMapper = breakMapper;
    }

    @Transactional
    public BreakItemDto addComment(Long breakId, AddBreakCommentRequest request) {
        BreakItem breakItem = findAuthorizedBreak(breakId);
        BreakComment comment = new BreakComment();
        comment.setBreakItem(breakItem);
        comment.setAction(request.action());
        comment.setComment(request.comment());
        comment.setCreatedAt(Instant.now());
        comment.setActorDn(userDirectoryService.personDn(userContext.getUsername()));
        breakCommentRepository.save(comment);
        return breakMapper.toDto(breakItem);
    }

    @Transactional
    public BreakItemDto updateStatus(Long breakId, UpdateBreakStatusRequest request) {
        BreakItem breakItem = findAuthorizedBreak(breakId);
        breakItem.setStatus(request.status());
        breakItemRepository.save(breakItem);

        BreakComment auditTrail = new BreakComment();
        auditTrail.setBreakItem(breakItem);
        auditTrail.setAction("STATUS_CHANGE");
        auditTrail.setComment("Status updated to " + request.status().name());
        auditTrail.setCreatedAt(Instant.now());
        auditTrail.setActorDn(userDirectoryService.personDn(userContext.getUsername()));
        breakCommentRepository.save(auditTrail);

        return breakMapper.toDto(breakItem);
    }

    private BreakItem findAuthorizedBreak(Long id) {
        BreakItem item = breakItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Break not found"));
        ensureAccess(item.getRun().getDefinition());
        return item;
    }

    private void ensureAccess(ReconciliationDefinition definition) {
        List<String> groups = userContext.getGroups();
        if (groups.isEmpty()) {
            throw new SecurityException("User is not associated with any security group");
        }
        List<AccessControlEntry> entries =
                accessControlEntryRepository.findByDefinitionAndLdapGroupDnIn(definition, groups);
        if (entries.isEmpty()) {
            throw new SecurityException("User lacks permissions for this break");
        }
    }
}
