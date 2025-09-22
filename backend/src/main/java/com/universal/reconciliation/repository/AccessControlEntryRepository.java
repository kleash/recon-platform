package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for reconciliation access control entries.
 */
public interface AccessControlEntryRepository extends JpaRepository<AccessControlEntry, Long> {

    List<AccessControlEntry> findByLdapGroupDnIn(List<String> ldapGroupDns);

    List<AccessControlEntry> findByDefinitionAndLdapGroupDnIn(ReconciliationDefinition definition, List<String> ldapGroupDns);
}
