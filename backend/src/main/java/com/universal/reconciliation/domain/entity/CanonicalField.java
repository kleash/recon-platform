package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Defines a canonical field used during dynamic reconciliation. Canonical
 * fields are mapped to one or more source-specific columns and drive
 * matching, filtering, and reporting behaviour.
 */
@Entity
@Table(name = "canonical_fields")
@Getter
@Setter
public class CanonicalField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column(name = "canonical_name", nullable = false)
    private String canonicalName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private FieldDataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_logic", nullable = false)
    private ComparisonLogic comparisonLogic;

    @Column(name = "threshold_percentage")
    private BigDecimal thresholdPercentage;

    @Column(name = "classifier_tag")
    private String classifierTag;

    @Column(name = "formatting_hint")
    private String formattingHint;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "canonicalField", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CanonicalFieldMapping> mappings = new LinkedHashSet<>();

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
