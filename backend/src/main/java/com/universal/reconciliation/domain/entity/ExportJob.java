package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.ExportFormat;
import com.universal.reconciliation.domain.enums.ExportJobStatus;
import com.universal.reconciliation.domain.enums.ExportJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks asynchronous export jobs and stores generated artefacts.
 */
@Entity
@Table(name = "export_jobs")
@Getter
@Setter
public class ExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private ReconciliationDefinition definition;

    @Column(nullable = false)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExportJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExportJobStatus status = ExportJobStatus.QUEUED;

    @Column(name = "filters_json", columnDefinition = "LONGTEXT")
    private String filtersJson;

    @Column(name = "settings_json", columnDefinition = "LONGTEXT")
    private String settingsJson;

    @Column(name = "owner_groups_json", columnDefinition = "LONGTEXT")
    private String ownerGroupsJson;

    @Lob
    @Column(name = "payload", columnDefinition = "LONGBLOB")
    private byte[] payload;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "timezone")
    private String timezone;

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
