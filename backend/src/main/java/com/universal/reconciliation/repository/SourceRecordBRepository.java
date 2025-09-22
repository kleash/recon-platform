package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.SourceRecordB;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for source B normalized data.
 */
public interface SourceRecordBRepository extends JpaRepository<SourceRecordB, Long> {

    Optional<SourceRecordB> findByTransactionId(String transactionId);

    @Query("select b from SourceRecordB b")
    Stream<SourceRecordB> streamAll();
}
