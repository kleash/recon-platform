package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.ExportJob;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence gateway for export job tracking.
 */
public interface ExportJobRepository extends JpaRepository<ExportJob, Long> {

    List<ExportJob> findByDefinitionAndOwnerOrderByCreatedAtDesc(ReconciliationDefinition definition, String owner);
}

