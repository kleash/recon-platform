package com.universal.reconciliation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column(nullable = false)
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

    @Column(name = "account_id")
    private String accountId;

    private String isin;

    private BigDecimal quantity;

    @Column(name = "market_value")
    private BigDecimal marketValue;

    @Column(name = "valuation_currency")
    private String valuationCurrency;

    @Column(name = "valuation_date")
    private LocalDate valuationDate;

    private String custodian;

    @Column(name = "portfolio_manager")
    private String portfolioManager;

}
