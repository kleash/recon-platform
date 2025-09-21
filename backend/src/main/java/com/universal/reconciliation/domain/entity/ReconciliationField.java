package com.universal.reconciliation.domain.entity;

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

/**
 * Configures how a specific field participates in reconciliation operations.
 */
@Entity
@Table(name = "reconciliation_fields")
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

    public Long getId() {
        return id;
    }

    public ReconciliationDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(ReconciliationDefinition definition) {
        this.definition = definition;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public FieldRole getRole() {
        return role;
    }

    public void setRole(FieldRole role) {
        this.role = role;
    }
}
