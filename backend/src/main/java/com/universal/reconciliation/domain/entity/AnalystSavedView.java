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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores analyst grid configurations (filters, layouts) that can be restored
 * quickly or shared with other users.
 */
@Entity
@Table(
        name = "analyst_saved_views",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_saved_view_token", columnNames = "shared_token")
        })
@Getter
@Setter
public class AnalystSavedView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "settings_json", columnDefinition = "LONGTEXT", nullable = false)
    private String settingsJson;

    @Column(name = "shared_token")
    private String sharedToken;

    @Column(name = "is_shared", nullable = false)
    private boolean shared;

    @Column(name = "is_default", nullable = false)
    private boolean defaultView;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public void touch() {
        this.updatedAt = Instant.now();
    }
}

