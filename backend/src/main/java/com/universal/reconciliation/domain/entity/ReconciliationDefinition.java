package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores the metadata that defines a reconciliation configuration.
 */
@Entity
@Table(name = "reconciliation_definitions")
@Getter
@Setter
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

    @Column(nullable = false)
    private boolean makerCheckerEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationLifecycleStatus status = ReconciliationLifecycleStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "owned_by")
    private String ownedBy;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "auto_trigger_enabled", nullable = false)
    private boolean autoTriggerEnabled;

    @Column(name = "auto_trigger_cron")
    private String autoTriggerCron;

    @Column(name = "auto_trigger_timezone")
    private String autoTriggerTimezone;

    @Column(name = "auto_trigger_grace_minutes")
    private Integer autoTriggerGraceMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_by")
    private String retiredBy;

    @Version
    private Long version;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReportTemplate> reportTemplates = new LinkedHashSet<>();

    /**
     * Legacy field metadata retained for backward compatibility while the
     * dynamic configuration model is introduced. New reconciliations should
     * rely on {@link #canonicalFields} instead.
     */
    @Deprecated(forRemoval = true)
    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReconciliationField> fields = new LinkedHashSet<>();

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReconciliationSource> sources = new LinkedHashSet<>();

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CanonicalField> canonicalFields = new LinkedHashSet<>();

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AccessControlEntry> accessControlEntries = new LinkedHashSet<>();

    public void touch() {
        this.updatedAt = Instant.now();
    }
}

