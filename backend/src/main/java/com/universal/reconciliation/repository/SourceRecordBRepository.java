package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.SourceRecordB;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for source B normalized data.
 */
public interface SourceRecordBRepository extends JpaRepository<SourceRecordB, Long> {

    Optional<SourceRecordB> findByTransactionId(String transactionId);
}
