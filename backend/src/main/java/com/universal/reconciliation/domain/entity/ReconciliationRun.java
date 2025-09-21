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

/**
 * Represents a specific execution of the reconciliation matching engine.
 */
@Entity
@Table(name = "reconciliation_runs")
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

    @Column(nullable = false)
    private int matchedCount;

    @Column(nullable = false)
    private int mismatchedCount;

    @Column(nullable = false)
    private int missingCount;

    @OneToMany(mappedBy = "run")
    private Set<BreakItem> breakItems = new LinkedHashSet<>();

    public Long getId() {
        return id;
    }

    public ReconciliationDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(ReconciliationDefinition definition) {
        this.definition = definition;
    }

    public Instant getRunDateTime() {
        return runDateTime;
    }

    public void setRunDateTime(Instant runDateTime) {
        this.runDateTime = runDateTime;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int matchedCount) {
        this.matchedCount = matchedCount;
    }

    public int getMismatchedCount() {
        return mismatchedCount;
    }

    public void setMismatchedCount(int mismatchedCount) {
        this.mismatchedCount = mismatchedCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public void setMissingCount(int missingCount) {
        this.missingCount = missingCount;
    }

    public Set<BreakItem> getBreakItems() {
        return breakItems;
    }
}
