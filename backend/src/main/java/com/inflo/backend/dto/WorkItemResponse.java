
package com.inflo.backend.dto;

import com.inflo.backend.entity.WorkItem;
import java.time.Instant;

public class WorkItemResponse {

    private Long id;
    private String title;
    private String description;
    private String type;
    private String priority;
    private String status;
    private String team;
    private Long createdById;
    private String createdByName;
    private Long assigneeId;
    private String assigneeName;
    private Long parentId;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant assignedAt;
    private Instant resolvedAt;
    private Instant escalatedAt;
    private boolean leadHelpRequested;

    public WorkItemResponse() {
    }

    public static WorkItemResponse from(WorkItem item) {
        WorkItemResponse response = new WorkItemResponse();

        response.id = item.getId();
        response.title = item.getTitle();
        response.description = item.getDescription();
        response.type = item.getType() != null ? item.getType().name() : null;
        response.priority = item.getPriority() != null ? item.getPriority().name() : null;
        response.status = item.getStatus() != null ? item.getStatus().name() : null;
        response.team = item.getTeam() != null && item.getTeam().getName() != null
                ? item.getTeam().getName().name() : null;

        response.createdById = item.getCreatedBy() != null ? item.getCreatedBy().getId() : null;
        response.createdByName = item.getCreatedBy() != null ? item.getCreatedBy().getFullName() : null;
        response.assigneeId = item.getAssignee() != null ? item.getAssignee().getId() : null;
        response.assigneeName = item.getAssignee() != null ? item.getAssignee().getFullName() : null;
        response.parentId = item.getParent() != null ? item.getParent().getId() : null;
        response.version = item.getVersion();

        response.createdAt = item.getCreatedAt();
        response.updatedAt = item.getUpdatedAt();
        response.assignedAt = item.getAssignedAt();
        response.resolvedAt = item.getResolvedAt();
        response.escalatedAt = item.getEscalatedAt();
        response.leadHelpRequested = item.isLeadHelpRequested();

        return response;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public String getTeam() { return team; }
    public Long getCreatedById() { return createdById; }
    public String getCreatedByName() { return createdByName; }
    public Long getAssigneeId() { return assigneeId; }
    public String getAssigneeName() { return assigneeName; }
    public Long getParentId() { return parentId; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public boolean isLeadHelpRequested() { return leadHelpRequested; }
}

