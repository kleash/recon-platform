package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.DataBatchStatus;
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
 * Captures a discrete load of records for a reconciliation source. Batches
 * are versioned so that multiple ingestion runs can be stored without data
 * loss.
 */
@Entity
@Table(name = "source_data_batches")
@Getter
@Setter
public class SourceDataBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private ReconciliationSource source;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataBatchStatus status = DataBatchStatus.PENDING;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt = Instant.now();

    @Column(name = "record_count")
    private Long recordCount;

    @Column(name = "checksum")
    private String checksum;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SourceDataRecord> records = new LinkedHashSet<>();
}
