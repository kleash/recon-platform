package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.SourceRecordA;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for source A normalized data.
 */
public interface SourceRecordARepository extends JpaRepository<SourceRecordA, Long> {

    Optional<SourceRecordA> findByTransactionId(String transactionId);

    @Query("select a from SourceRecordA a")
    Stream<SourceRecordA> streamAll();
}
