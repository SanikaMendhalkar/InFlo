
package com.inflo.backend.dto;

import com.inflo.backend.enums.Priority;
import com.inflo.backend.enums.WorkItemStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateWorkItemRequest {

    @NotNull(message = "Status is required")
    private WorkItemStatus status;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Version is required for concurrency control")
    private Long version;

    public UpdateWorkItemRequest() {
    }

    public WorkItemStatus getStatus() {
        return status;
    }

    public void setStatus(WorkItemStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
