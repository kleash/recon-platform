package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.SourceRecordA;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for source A normalized data.
 */
public interface SourceRecordARepository extends JpaRepository<SourceRecordA, Long> {

    Optional<SourceRecordA> findByDefinitionAndTransactionId(
            ReconciliationDefinition definition, String transactionId);

    List<SourceRecordA> findByDefinition(ReconciliationDefinition definition);

    @Query("select a from SourceRecordA a where a.definition = :definition")
    Stream<SourceRecordA> streamByDefinition(@Param("definition") ReconciliationDefinition definition);
}
