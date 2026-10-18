
package com.inflo.backend.entity;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "assignment_history")
public class AssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_item_id", nullable = false)
    private WorkItem workItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_assignee_id")
    private User previousAssignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_assignee_id", nullable = false)
    private User newAssignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_id", nullable = false)
    private User assignedBy;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    protected AssignmentHistory() {
    }

    public AssignmentHistory(
            WorkItem workItem,
            User previousAssignee,
            User newAssignee,
            User assignedBy,
            Instant assignedAt) {
        this.workItem = workItem;
        this.previousAssignee = previousAssignee;
        this.newAssignee = newAssignee;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public Long getId() {
        return id;
    }

    public WorkItem getWorkItem() {
        return workItem;
    }

    public User getPreviousAssignee() {
        return previousAssignee;
    }

    public User getNewAssignee() {
        return newAssignee;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
