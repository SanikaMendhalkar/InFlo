
package com.inflo.backend.dto;

import java.time.Instant;

import com.inflo.backend.entity.ActivityLog;
import com.inflo.backend.enums.ActivityAction;

public record ActivityLogResponse(
        Long id,
        Long workItemId,
        Long actorId,
        String actorName,
        ActivityAction action,
        String details,
        Instant occurredAt) {

    public static ActivityLogResponse from(ActivityLog log) {
        return new ActivityLogResponse(
                log.getId(),
                log.getWorkItem().getId(),
                log.getActor() == null ? null : log.getActor().getId(),
                log.getActor() == null ? null : log.getActor().getFullName(),
                log.getAction(),
                log.getDetails(),
                log.getOccurredAt()
        );
    }
}
