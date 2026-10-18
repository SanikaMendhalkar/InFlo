
package com.inflo.backend.repository;

import com.inflo.backend.entity.IdempotencyRecord;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByActorIdAndIdempotencyKey(
        Long actorId,
        String idempotencyKey
    );
}

