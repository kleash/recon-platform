package com.universal.reconciliation.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps a canonical field to a concrete column (or attribute) within a
 * configured reconciliation source. Transformations can be applied to
 * normalise values during ingestion.
 */
@Entity
@Table(name = "canonical_field_mappings")
@Getter
@Setter
public class CanonicalFieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_field_id", nullable = false)
    private CanonicalField canonicalField;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private ReconciliationSource source;

    @Column(name = "source_column", nullable = false)
    private String sourceColumn;

    @Column(name = "transformation_expression")
    private String transformationExpression;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "source_date_format")
    private String sourceDateFormat;

    @Column(name = "target_date_format")
    private String targetDateFormat;

    @Column(name = "ordinal_position")
    private Integer ordinalPosition;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "mapping", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private Set<CanonicalFieldTransformation> transformations = new LinkedHashSet<>();

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
