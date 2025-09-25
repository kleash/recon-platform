package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
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
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Immutable audit record for maker/checker transitions applied to a break item.
 */
@Entity
@Table(name = "break_workflow_audit")
@Getter
@Setter
public class BreakWorkflowAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "break_item_id", nullable = false)
    private BreakItem breakItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private BreakStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private BreakStatus newStatus;

    @Column(name = "actor_dn", nullable = false)
    private String actorDn;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false)
    private AccessRole actorRole;

    @Column(length = 2000)
    private String comment;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
