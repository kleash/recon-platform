package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.domain.enums.BreakStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for break items.
 */
public interface BreakItemRepository extends JpaRepository<BreakItem, Long>, JpaSpecificationExecutor<BreakItem> {

    @EntityGraph(attributePaths = {"comments", "workflowAudits", "classificationValues"})
    List<BreakItem> findByRunOrderByDetectedAtAsc(ReconciliationRun run);

    @EntityGraph(attributePaths = {"comments", "workflowAudits", "classificationValues"})
    List<BreakItem> findByRunDefinitionIdAndStatusOrderByDetectedAtAsc(
            Long definitionId, BreakStatus status, Pageable pageable);
}
