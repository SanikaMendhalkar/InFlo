
package com.inflo.backend.repository;

import java.util.List;

import com.inflo.backend.entity.AssignmentHistory;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentHistoryRepository
        extends JpaRepository<AssignmentHistory, Long> {

    @EntityGraph(attributePaths = {
            "previousAssignee",
            "newAssignee",
            "assignedBy"
    })
    @Query("""
            SELECT h
            FROM AssignmentHistory h
            WHERE h.workItem.id = :workItemId
            ORDER BY h.assignedAt DESC, h.id DESC
            """)
    List<AssignmentHistory> findHistoryByWorkItemId(
            @Param("workItemId") Long workItemId);
}
