package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.AnalystSavedView;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for analyst saved grid views.
 */
public interface AnalystSavedViewRepository extends JpaRepository<AnalystSavedView, Long> {

    List<AnalystSavedView> findByDefinitionAndOwnerOrderByUpdatedAtDesc(
            ReconciliationDefinition definition, String owner);

    Optional<AnalystSavedView> findBySharedToken(String sharedToken);

    Optional<AnalystSavedView> findByDefinitionAndOwnerAndDefaultView(
            ReconciliationDefinition definition, String owner, boolean defaultView);
}

