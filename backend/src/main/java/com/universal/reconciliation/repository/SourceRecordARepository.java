package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.SourceRecordA;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for source A normalized data.
 */
public interface SourceRecordARepository extends JpaRepository<SourceRecordA, Long> {

    Optional<SourceRecordA> findByTransactionId(String transactionId);
}
