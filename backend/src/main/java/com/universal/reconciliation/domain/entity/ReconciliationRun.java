package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.RunStatus;
import com.universal.reconciliation.domain.enums.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a specific execution of the reconciliation matching engine.
 */
@Entity
@Table(name = "reconciliation_runs")
@Getter
@Setter
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column(nullable = false)
    private Instant runDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "trigger_comments")
    private String triggerComments;

    @Column(name = "trigger_correlation_id")
    private String triggerCorrelationId;

    @Column(nullable = false)
    private int matchedCount;

    @Column(nullable = false)
    private int mismatchedCount;

    @Column(nullable = false)
    private int missingCount;

    @OneToMany(mappedBy = "run")
    private Set<BreakItem> breakItems = new LinkedHashSet<>();

}
