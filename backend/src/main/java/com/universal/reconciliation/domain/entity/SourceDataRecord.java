package com.universal.reconciliation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores a source record payload in its normalised JSON form. The canonical
 * key is pre-computed to speed up matching across sources.
 */
@Entity
@Table(
        name = "source_data_records",
        indexes = {
            @Index(name = "idx_source_record_batch", columnList = "batch_id"),
            @Index(name = "idx_source_record_canonical_key", columnList = "canonical_key")
        })
@Getter
@Setter
public class SourceDataRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private SourceDataBatch batch;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "canonical_key", nullable = false)
    private String canonicalKey;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt = Instant.now();
}
