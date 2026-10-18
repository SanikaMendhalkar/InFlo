
package com.inflo.backend.dto;

import java.time.Instant;

import com.inflo.backend.entity.AssignmentHistory;

public record AssignmentHistoryResponse(
        Long id,
        Long workItemId,
        Long previousAssigneeId,
        String previousAssigneeName,
        Long newAssigneeId,
        String newAssigneeName,
        Long assignedById,
        String assignedByName,
        Instant assignedAt) {

    public static AssignmentHistoryResponse from(AssignmentHistory history) {
        return new AssignmentHistoryResponse(
                history.getId(),
                history.getWorkItem().getId(),
                history.getPreviousAssignee() == null
                        ? null : history.getPreviousAssignee().getId(),
                history.getPreviousAssignee() == null
                        ? null : history.getPreviousAssignee().getFullName(),
                history.getNewAssignee().getId(),
                history.getNewAssignee().getFullName(),
                history.getAssignedBy().getId(),
                history.getAssignedBy().getFullName(),
                history.getAssignedAt());
    }
}
