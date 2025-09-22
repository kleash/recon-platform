package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.SystemEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Persists system-level events for display in the activity feed.
 */
@Entity
@Table(name = "system_activity_logs")
@Getter
@Setter
public class SystemActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemEventType eventType;

    @Column(nullable = false, length = 2000)
    private String details;

    @Column(nullable = false)
    private Instant recordedAt;
}

