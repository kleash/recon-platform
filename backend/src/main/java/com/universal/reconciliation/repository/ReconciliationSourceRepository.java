package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for reconciliation source metadata.
 */
public interface ReconciliationSourceRepository extends JpaRepository<ReconciliationSource, Long> {

    List<ReconciliationSource> findByDefinition(ReconciliationDefinition definition);

    Optional<ReconciliationSource> findByDefinitionAndCode(ReconciliationDefinition definition, String code);
}
