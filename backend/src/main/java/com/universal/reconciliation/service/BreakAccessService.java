package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Centralises the maker/checker access rules for break actions.
 */
@Service
public class BreakAccessService {

    private final AccessControlEntryRepository accessControlEntryRepository;

    public BreakAccessService(AccessControlEntryRepository accessControlEntryRepository) {
        this.accessControlEntryRepository = accessControlEntryRepository;
    }

    public List<AccessControlEntry> findEntries(ReconciliationDefinition definition, List<String> groups) {
        if (groups.isEmpty()) {
            throw new SecurityException("User is not associated with any security group");
        }
        return accessControlEntryRepository.findByDefinitionAndLdapGroupDnIn(definition, groups);
    }

    public void assertCanView(BreakItem breakItem, List<AccessControlEntry> entries) {
        if (!canView(breakItem, entries)) {
            throw new SecurityException("User lacks permissions for this break");
        }
    }

    public boolean canView(BreakItem breakItem, List<AccessControlEntry> entries) {
        return !matchingEntries(breakItem, entries).isEmpty();
    }

    public void assertCanComment(BreakItem breakItem, List<AccessControlEntry> entries) {
        if (!canComment(breakItem, entries)) {
            throw new SecurityException("User is not authorised to comment on this break");
        }
    }

    public boolean canComment(BreakItem breakItem, List<AccessControlEntry> entries) {
        List<AccessControlEntry> scopedEntries = matchingEntries(breakItem, entries);
        return hasRole(scopedEntries, AccessRole.MAKER) || hasRole(scopedEntries, AccessRole.CHECKER);
    }

    public List<BreakStatus> allowedStatuses(
            BreakItem breakItem, ReconciliationDefinition definition, List<AccessControlEntry> entries) {
        List<AccessControlEntry> scopedEntries = matchingEntries(breakItem, entries);
        boolean maker = hasRole(scopedEntries, AccessRole.MAKER);
        boolean checker = hasRole(scopedEntries, AccessRole.CHECKER);

        Set<BreakStatus> statuses = new LinkedHashSet<>();
        BreakStatus current = breakItem.getStatus();

        if (definition.isMakerCheckerEnabled()) {
            if (maker) {
                if (current != BreakStatus.PENDING_APPROVAL) {
                    statuses.add(BreakStatus.PENDING_APPROVAL);
                }
                if (current == BreakStatus.PENDING_APPROVAL) {
                    statuses.add(BreakStatus.OPEN);
                }
            }
            if (checker && current == BreakStatus.PENDING_APPROVAL) {
                statuses.add(BreakStatus.CLOSED);
                statuses.add(BreakStatus.OPEN);
            }
        } else if (maker || checker) {
            if (current != BreakStatus.OPEN) {
                statuses.add(BreakStatus.OPEN);
            }
            if (current != BreakStatus.CLOSED) {
                statuses.add(BreakStatus.CLOSED);
            }
        }

        return new ArrayList<>(statuses);
    }

    public void assertTransitionAllowed(
            BreakItem breakItem,
            ReconciliationDefinition definition,
            List<AccessControlEntry> entries,
            BreakStatus targetStatus) {
        List<BreakStatus> allowed = allowedStatuses(breakItem, definition, entries);
        if (!allowed.contains(targetStatus)) {
            throw new SecurityException("User cannot transition break to " + targetStatus);
        }
    }

    private List<AccessControlEntry> matchingEntries(BreakItem breakItem, List<AccessControlEntry> entries) {
        return entries.stream().filter(entry -> matches(entry.getProduct(), breakItem.getProduct())
                && matches(entry.getSubProduct(), breakItem.getSubProduct())
                && matches(entry.getEntityName(), breakItem.getEntityName()))
                .toList();
    }

    private boolean hasRole(List<AccessControlEntry> entries, AccessRole role) {
        return entries.stream().anyMatch(entry -> role.equals(entry.getRole()));
    }

    private boolean matches(String entryValue, String breakValue) {
        return entryValue == null || Objects.equals(entryValue, breakValue);
    }
}

