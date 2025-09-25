package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * Describes a logical data source that contributes records to a
 * reconciliation definition. Sources are configured via metadata so new
 * reconciliations can be introduced without code changes.
 */
@Entity
@Table(name = "reconciliation_sources")
@Getter
@Setter
public class ReconciliationSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionAdapterType adapterType;

    @Column(nullable = false)
    private boolean anchor;

    @Column(length = 512)
    private String description;

    @Column(name = "connection_config", columnDefinition = "TEXT")
    private String connectionConfig;

    @Column(name = "arrival_expectation", length = 256)
    private String arrivalExpectation;

    @Column(name = "arrival_timezone")
    private String arrivalTimezone;

    @Column(name = "arrival_sla_minutes")
    private Integer arrivalSlaMinutes;

    @Column(name = "adapter_options", columnDefinition = "TEXT")
    private String adapterOptions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CanonicalFieldMapping> fieldMappings = new LinkedHashSet<>();

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SourceDataBatch> batches = new LinkedHashSet<>();

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
