package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for break items.
 */
public interface BreakItemRepository extends JpaRepository<BreakItem, Long> {

    @EntityGraph(attributePaths = {"comments", "workflowAudits"})
    List<BreakItem> findByRunOrderByDetectedAtAsc(ReconciliationRun run);
}
