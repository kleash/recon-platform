package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for ingestion batch metadata.
 */
public interface SourceDataBatchRepository extends JpaRepository<SourceDataBatch, Long> {

    List<SourceDataBatch> findBySourceOrderByIngestedAtDesc(ReconciliationSource source);

    Optional<SourceDataBatch> findFirstBySourceOrderByIngestedAtDesc(ReconciliationSource source);
}
