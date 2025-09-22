package com.universal.reconciliation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a normalized record from source system A.
 */
@Entity
@Table(name = "source_a_records")
@Getter
@Setter
public class SourceRecordA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false)
    private String subProduct;

    @Column(nullable = false, name = "entity_name")
    private String entityName;

}
