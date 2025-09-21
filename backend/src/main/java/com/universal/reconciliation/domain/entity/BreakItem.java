package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stores a single detected break between two data sources.
 */
@Entity
@Table(name = "break_items")
public class BreakItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakType breakType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakStatus status = BreakStatus.OPEN;

    @Column(nullable = false)
    private Instant detectedAt;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String sourceAJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String sourceBJson;

    @OneToMany(mappedBy = "breakItem")
    private Set<BreakComment> comments = new LinkedHashSet<>();

    public Long getId() {
        return id;
    }

    public ReconciliationRun getRun() {
        return run;
    }

    public void setRun(ReconciliationRun run) {
        this.run = run;
    }

    public BreakType getBreakType() {
        return breakType;
    }

    public void setBreakType(BreakType breakType) {
        this.breakType = breakType;
    }

    public BreakStatus getStatus() {
        return status;
    }

    public void setStatus(BreakStatus status) {
        this.status = status;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getSourceAJson() {
        return sourceAJson;
    }

    public void setSourceAJson(String sourceAJson) {
        this.sourceAJson = sourceAJson;
    }

    public String getSourceBJson() {
        return sourceBJson;
    }

    public void setSourceBJson(String sourceBJson) {
        this.sourceBJson = sourceBJson;
    }

    public Set<BreakComment> getComments() {
        return comments;
    }
}
