package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.SourceRecordB;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for source B normalized data.
 */
public interface SourceRecordBRepository extends JpaRepository<SourceRecordB, Long> {

    Optional<SourceRecordB> findByDefinitionAndTransactionId(
            ReconciliationDefinition definition, String transactionId);

    List<SourceRecordB> findByDefinition(ReconciliationDefinition definition);

    @Query("select b from SourceRecordB b where b.definition = :definition")
    Stream<SourceRecordB> streamByDefinition(@Param("definition") ReconciliationDefinition definition);
}
