package com.inflo.backend.entity;

import com.inflo.backend.enums.ActivityAction;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "activity_logs",
    indexes = {
        @Index(name = "idx_activity_item_time", columnList = "work_item_id, occurred_at"),
        @Index(name = "idx_activity_actor_time", columnList = "actor_id, occurred_at")
    }
)
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_item_id", nullable = false)
    private WorkItem workItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityAction action;

    @Column(nullable = false, length = 2000)
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public ActivityLog() {}

    public ActivityLog(
        WorkItem workItem,
        User actor,
        ActivityAction action,
        String details
    ) {
        this.workItem = workItem;
        this.actor = actor;
        this.action = action;
        this.details = details;
    }

    public Long getId() { return id; }

    public WorkItem getWorkItem() { return workItem; }

    public User getActor() { return actor; }

    public ActivityAction getAction() { return action; }

    public String getDetails() { return details; }

    public Instant getOccurredAt() { return occurredAt; }
}
