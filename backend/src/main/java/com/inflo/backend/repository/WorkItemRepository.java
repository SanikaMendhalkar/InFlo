
package com.inflo.backend.repository;

import java.util.List;

import com.inflo.backend.entity.WorkItem;
import com.inflo.backend.enums.Priority;
import com.inflo.backend.enums.TeamType;
import com.inflo.backend.enums.WorkItemStatus;
import com.inflo.backend.enums.WorkItemType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemRepository extends JpaRepository<WorkItem, Long> {

    // Filters by team name, status, priority, type, and search text.
    @EntityGraph(attributePaths = {
        "team",
        "createdBy",
        "assignee",
        "parent",
        "updatedBy",
        "assignedBy",
        "resolvedBy"
    })
    @Query(
        value = """
            SELECT w FROM WorkItem w
            WHERE (:team IS NULL OR w.team.name = :team)
              AND (:status IS NULL OR w.status = :status)
              AND (:priority IS NULL OR w.priority = :priority)
              AND (:type IS NULL OR w.type = :type)
              AND (
                  :search IS NULL
                  OR LOWER(w.title) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(w.description) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """,
        countQuery = """
            SELECT COUNT(w) FROM WorkItem w
            WHERE (:team IS NULL OR w.team.name = :team)
              AND (:status IS NULL OR w.status = :status)
              AND (:priority IS NULL OR w.priority = :priority)
              AND (:type IS NULL OR w.type = :type)
              AND (
                  :search IS NULL
                  OR LOWER(w.title) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(w.description) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """
    )
    Page<WorkItem> searchWorkItems(
        @Param("team") TeamType team,
        @Param("status") WorkItemStatus status,
        @Param("priority") Priority priority,
        @Param("type") WorkItemType type,
        @Param("search") String search,
        Pageable pageable
    );

    // Filters by team ID, visibility scope, assignee, status,
    // priority, and search text. Filtering remains in the database.
    @EntityGraph(attributePaths = {
        "team",
        "createdBy",
        "assignee",
        "parent",
        "updatedBy",
        "assignedBy",
        "resolvedBy"
    })
    @Query(
        value = """
            SELECT w FROM WorkItem w
            WHERE (:visibleTeamId IS NULL OR w.team.id = :visibleTeamId)
              AND (:teamId IS NULL OR w.team.id = :teamId)
              AND (:assigneeId IS NULL OR w.assignee.id = :assigneeId)
              AND (:status IS NULL OR w.status = :status)
              AND (:priority IS NULL OR w.priority = :priority)
              AND (
                  :search IS NULL
                  OR LOWER(w.title) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(w.description) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """,
        countQuery = """
            SELECT COUNT(w) FROM WorkItem w
            WHERE (:visibleTeamId IS NULL OR w.team.id = :visibleTeamId)
              AND (:teamId IS NULL OR w.team.id = :teamId)
              AND (:assigneeId IS NULL OR w.assignee.id = :assigneeId)
              AND (:status IS NULL OR w.status = :status)
              AND (:priority IS NULL OR w.priority = :priority)
              AND (
                  :search IS NULL
                  OR LOWER(w.title) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(w.description) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """
    )
    Page<WorkItem> searchWorkItems(
        @Param("visibleTeamId") Long visibleTeamId,
        @Param("teamId") Long teamId,
        @Param("assigneeId") Long assigneeId,
        @Param("status") WorkItemStatus status,
        @Param("priority") Priority priority,
        @Param("search") String search,
        Pageable pageable
    );

    // Counts active work by assignee for automatic workload balancing.
    long countByAssigneeIdAndStatusIn(
        Long assigneeId,
        List<WorkItemStatus> statuses
    );
}
