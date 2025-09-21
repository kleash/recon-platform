package com.universal.reconciliation.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stores the metadata that defines a reconciliation configuration.
 */
@Entity
@Table(name = "reconciliation_definitions")
public class ReconciliationDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReconciliationField> fields = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean makerCheckerEnabled;

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<ReconciliationField> getFields() {
        return fields;
    }

    public boolean isMakerCheckerEnabled() {
        return makerCheckerEnabled;
    }

    public void setMakerCheckerEnabled(boolean makerCheckerEnabled) {
        this.makerCheckerEnabled = makerCheckerEnabled;
    }
}
