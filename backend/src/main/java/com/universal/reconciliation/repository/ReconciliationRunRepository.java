package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for reconciliation runs.
 */
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    List<ReconciliationRun> findByDefinitionOrderByRunDateTimeDesc(ReconciliationDefinition definition);

    Optional<ReconciliationRun> findTopByDefinitionOrderByRunDateTimeDesc(ReconciliationDefinition definition);
}
