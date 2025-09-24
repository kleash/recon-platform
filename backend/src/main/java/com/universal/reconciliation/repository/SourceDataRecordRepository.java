package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.SourceDataBatch;
import com.universal.reconciliation.domain.entity.SourceDataRecord;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for canonicalised source records.
 */
public interface SourceDataRecordRepository extends JpaRepository<SourceDataRecord, Long> {

    List<SourceDataRecord> findByBatch(SourceDataBatch batch);

    @Query("select r from SourceDataRecord r where r.batch = :batch")
    Stream<SourceDataRecord> streamByBatch(@Param("batch") SourceDataBatch batch);

    List<SourceDataRecord> findByBatchAndCanonicalKeyIn(SourceDataBatch batch, List<String> canonicalKeys);
}
