package com.universal.reconciliation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Records actions and commentary associated with a break item.
 */
@Entity
@Table(name = "break_comments")
@Getter
@Setter
public class BreakComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "break_item_id", nullable = false)
    private BreakItem breakItem;

    @Column(nullable = false)
    private String actorDn;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false, length = 2000)
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;

}
