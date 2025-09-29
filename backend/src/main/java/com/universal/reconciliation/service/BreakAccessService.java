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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Centralises the maker/checker access rules for break actions.
 */
@Service
public class BreakAccessService {

    private static final Logger log = LoggerFactory.getLogger(BreakAccessService.class);

    private final AccessControlEntryRepository accessControlEntryRepository;

    public BreakAccessService(AccessControlEntryRepository accessControlEntryRepository) {
        this.accessControlEntryRepository = accessControlEntryRepository;
    }

    public List<AccessControlEntry> findEntries(ReconciliationDefinition definition, List<String> groups) {
        if (groups.isEmpty()) {
            throw new AccessDeniedException("User is not associated with any security group");
        }
        List<AccessControlEntry> entries = accessControlEntryRepository.findByDefinitionAndLdapGroupDnIn(definition, groups);
        if (log.isDebugEnabled()) {
            log.debug(
                    "breakAccess findEntries: definition={} groups={} entries={}",
                    definition.getCode(),
                    groups,
                    entries.stream()
                            .map(entry -> entry.getLdapGroupDn() + ":" + entry.getRole() + "["
                                    + valueOr(entry.getProduct()) + "/" + valueOr(entry.getSubProduct()) + "/"
                                    + valueOr(entry.getEntityName()) + "]")
                            .toList());
        }
        return entries;
    }

    public void assertCanView(BreakItem breakItem, List<AccessControlEntry> entries) {
        if (!canView(breakItem, entries)) {
            String entrySummary = entries.stream()
                    .map(entry -> String.format(
                            "%s:%s[%s/%s/%s]",
                            entry.getLdapGroupDn(),
                            entry.getRole(),
                            valueOr(entry.getProduct()),
                            valueOr(entry.getSubProduct()),
                            valueOr(entry.getEntityName())))
                    .collect(Collectors.joining(", "));
            throw new AccessDeniedException(String.format(
                    "User lacks permissions for this break (breakId=%d, product=%s, subProduct=%s, entity=%s, entries=%s)",
                    breakItem.getId(),
                    valueOr(breakItem.getProduct()),
                    valueOr(breakItem.getSubProduct()),
                    valueOr(breakItem.getEntityName()),
                    entrySummary.isEmpty() ? "<none>" : entrySummary));
        }
    }

    public boolean canView(BreakItem breakItem, List<AccessControlEntry> entries) {
        return !scopedEntries(breakItem, entries).isEmpty();
    }

    public void assertCanComment(BreakItem breakItem, List<AccessControlEntry> entries) {
        if (!canComment(breakItem, entries)) {
            throw new AccessDeniedException("User is not authorised to comment on this break");
        }
    }

    public boolean canComment(BreakItem breakItem, List<AccessControlEntry> entries) {
        List<AccessControlEntry> scopedEntries = scopedEntries(breakItem, entries);
        return hasRole(scopedEntries, AccessRole.MAKER) || hasRole(scopedEntries, AccessRole.CHECKER);
    }

    public List<BreakStatus> allowedStatuses(
            BreakItem breakItem, ReconciliationDefinition definition, List<AccessControlEntry> entries) {
        List<AccessControlEntry> scopedEntries = scopedEntries(breakItem, entries);
        boolean maker = hasRole(scopedEntries, AccessRole.MAKER);
        boolean checker = hasRole(scopedEntries, AccessRole.CHECKER) && !maker;

        Set<BreakStatus> statuses = new LinkedHashSet<>();
        BreakStatus current = breakItem.getStatus();

        if (definition.isMakerCheckerEnabled()) {
            if (maker) {
                if (current == BreakStatus.OPEN || current == BreakStatus.REJECTED) {
                    statuses.add(BreakStatus.PENDING_APPROVAL);
                }
                if (current == BreakStatus.PENDING_APPROVAL) {
                    statuses.add(BreakStatus.OPEN);
                }
                if (current == BreakStatus.REJECTED) {
                    statuses.add(BreakStatus.OPEN);
                }
            }
            if (checker && current == BreakStatus.PENDING_APPROVAL) {
                statuses.add(BreakStatus.CLOSED);
                statuses.add(BreakStatus.REJECTED);
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
            throw new AccessDeniedException("User cannot transition break to " + targetStatus);
        }
    }

    public AccessRole resolveActorRole(
            BreakItem breakItem,
            ReconciliationDefinition definition,
            List<AccessControlEntry> entries,
            BreakStatus targetStatus) {
        List<AccessControlEntry> scopedEntries = scopedEntries(breakItem, entries);
        boolean maker = hasRole(scopedEntries, AccessRole.MAKER);
        boolean checker = hasRole(scopedEntries, AccessRole.CHECKER) && !maker;

        if (definition.isMakerCheckerEnabled()) {
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

    public List<AccessControlEntry> scopedEntries(BreakItem breakItem, List<AccessControlEntry> entries) {
        return matchingEntries(breakItem, entries);
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

    private String valueOr(String value) {
        return value == null || value.isBlank() ? "<null>" : value;
    }
}
