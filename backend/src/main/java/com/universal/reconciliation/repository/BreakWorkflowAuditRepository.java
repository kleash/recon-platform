package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.BreakWorkflowAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for maker/checker workflow audit trail entries.
 */
public interface BreakWorkflowAuditRepository extends JpaRepository<BreakWorkflowAudit, Long> {}
