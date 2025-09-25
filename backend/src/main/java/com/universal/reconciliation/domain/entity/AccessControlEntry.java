package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.AccessRole;
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
 * Binds LDAP security groups to reconciliation definitions with
 * fine-grained dimensional access in Phase 2.
 */
@Entity
@Table(name = "access_control_entries")
@Getter
@Setter
public class AccessControlEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ldapGroupDn;

    @ManyToOne
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column
    private String product;

    @Column(name = "sub_product")
    private String subProduct;

    @Column(name = "entity_name")
    private String entityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessRole role;

    @Column(name = "notify_on_publish", nullable = false)
    private boolean notifyOnPublish;

    @Column(name = "notify_on_ingestion_failure", nullable = false)
    private boolean notifyOnIngestionFailure;

    @Column(name = "notification_channel")
    private String notificationChannel;
}

