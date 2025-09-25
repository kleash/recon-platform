package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for reconciliation definitions.
 */
public interface ReconciliationDefinitionRepository
        extends JpaRepository<ReconciliationDefinition, Long>,
                JpaSpecificationExecutor<ReconciliationDefinition> {

    Optional<ReconciliationDefinition> findByCode(String code);
}
