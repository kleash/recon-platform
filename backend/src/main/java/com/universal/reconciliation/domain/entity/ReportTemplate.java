package com.universal.reconciliation.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Defines the configurable structure of an exported reconciliation report.
 */
@Entity
@Table(name = "report_templates")
@Getter
@Setter
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "definition_id")
    private ReconciliationDefinition definition;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean includeMatched;

    @Column(nullable = false)
    private boolean includeMismatched;

    @Column(nullable = false)
    private boolean includeMissing;

    @Column(nullable = false)
    private boolean highlightDifferences;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReportColumn> columns = new LinkedHashSet<>();
}
