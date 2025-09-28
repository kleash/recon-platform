package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.TransformationType;
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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single transformation rule applied to a source column prior to
 * canonical field normalisation.
 */
@Entity
@Table(name = "canonical_field_transformations")
@Getter
@Setter
public class CanonicalFieldTransformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "mapping_id", nullable = false)
    private CanonicalFieldMapping mapping;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransformationType type;

    @Column(name = "expression", columnDefinition = "TEXT")
    private String expression;

    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configuration;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public void touch() {
        this.updatedAt = Instant.now();
    }
}

