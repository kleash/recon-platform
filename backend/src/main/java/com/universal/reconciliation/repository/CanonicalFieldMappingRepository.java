package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for canonical field to source column mappings.
 */
public interface CanonicalFieldMappingRepository extends JpaRepository<CanonicalFieldMapping, Long> {

    List<CanonicalFieldMapping> findByCanonicalField(CanonicalField field);

    List<CanonicalFieldMapping> findBySource(ReconciliationSource source);
}
