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

/**
 * Records actions and commentary associated with a break item.
 */
@Entity
@Table(name = "break_comments")
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

    public Long getId() {
        return id;
    }

    public BreakItem getBreakItem() {
        return breakItem;
    }

    public void setBreakItem(BreakItem breakItem) {
        this.breakItem = breakItem;
    }

    public String getActorDn() {
        return actorDn;
    }

    public void setActorDn(String actorDn) {
        this.actorDn = actorDn;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
