package com.universal.reconciliation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Binds LDAP security groups to reconciliation definitions in Phase 1.
 */
@Entity
@Table(name = "access_control_entries")
public class AccessControlEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ldapGroupDn;

    @ManyToOne
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column(nullable = false)
    private String permissionScope;

    public Long getId() {
        return id;
    }

    public String getLdapGroupDn() {
        return ldapGroupDn;
    }

    public void setLdapGroupDn(String ldapGroupDn) {
        this.ldapGroupDn = ldapGroupDn;
    }

    public ReconciliationDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(ReconciliationDefinition definition) {
        this.definition = definition;
    }

    public String getPermissionScope() {
        return permissionScope;
    }

    public void setPermissionScope(String permissionScope) {
        this.permissionScope = permissionScope;
    }
}
