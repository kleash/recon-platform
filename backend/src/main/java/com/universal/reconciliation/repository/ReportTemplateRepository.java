package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.ReportTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for configured report templates.
 */
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    @EntityGraph(attributePaths = "columns")
    Optional<ReportTemplate> findTopByDefinitionIdOrderById(Long definitionId);
}
