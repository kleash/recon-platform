package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.ReportColumnSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Declares a single column inside a reconciliation report template.
 */
@Entity
@Table(name = "report_columns")
@Getter
@Setter
public class ReportColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id")
    private ReportTemplate template;

    @Column(nullable = false)
    private String header;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportColumnSource source;

    @Column(name = "source_field")
    private String sourceField;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "highlight_differences", nullable = false)
    private boolean highlightDifferences;
}
