package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for canonical field metadata.
 */
public interface CanonicalFieldRepository extends JpaRepository<CanonicalField, Long> {

    List<CanonicalField> findByDefinitionOrderByDisplayOrderAsc(ReconciliationDefinition definition);

    Optional<CanonicalField> findByDefinitionAndCanonicalName(
            ReconciliationDefinition definition, String canonicalName);
}
