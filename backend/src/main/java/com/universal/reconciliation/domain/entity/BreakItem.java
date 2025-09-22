package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.BreakType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores a single detected break between two data sources.
 */
@Entity
@Table(name = "break_items")
@Getter
@Setter
public class BreakItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakType breakType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakStatus status = BreakStatus.OPEN;

    @Column
    private String product;

    @Column(name = "sub_product")
    private String subProduct;

    @Column(name = "entity_name")
    private String entityName;

    @Column(nullable = false)
    private Instant detectedAt;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String sourceAJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String sourceBJson;

    @OneToMany(mappedBy = "breakItem")
    private Set<BreakComment> comments = new LinkedHashSet<>();

}
