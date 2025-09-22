package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Configures how a specific field participates in reconciliation operations.
 */
@Entity
@Table(name = "reconciliation_fields")
@Getter
@Setter
public class ReconciliationField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column(nullable = false)
    private String sourceField;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldDataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComparisonLogic comparisonLogic;

    private BigDecimal thresholdPercentage;

}
