package com.inflo.backend.repository;

import com.inflo.backend.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findByWorkItemIdOrderByOccurredAtDesc(
        Long workItemId,
        Pageable pageable
    );
}
