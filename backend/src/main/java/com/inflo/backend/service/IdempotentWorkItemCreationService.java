
package com.inflo.backend.service;

import com.inflo.backend.dto.CreateWorkItemRequest;
import com.inflo.backend.dto.WorkItemResponse;
import com.inflo.backend.entity.IdempotencyRecord;
import com.inflo.backend.entity.WorkItem;
import com.inflo.backend.repository.IdempotencyRecordRepository;
import com.inflo.backend.repository.WorkItemRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class IdempotentWorkItemCreationService {

    private final JdbcTemplate jdbcTemplate;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemService workItemService;

    public IdempotentWorkItemCreationService(
            JdbcTemplate jdbcTemplate,
            IdempotencyRecordRepository idempotencyRepository,
            WorkItemRepository workItemRepository,
            WorkItemService workItemService) {

        this.jdbcTemplate = jdbcTemplate;
        this.idempotencyRepository = idempotencyRepository;
        this.workItemRepository = workItemRepository;
        this.workItemService = workItemService;
    }

    @Transactional
    public WorkItemResponse create(
            Long actorId,
            String key,
            CreateWorkItemRequest request) {

        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1 to 100 characters");
        }

        String requestHash = hashRequest(request);

        int inserted = jdbcTemplate.update("""
                INSERT INTO idempotency_records
                    (actor_id, idempotency_key, request_hash, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (actor_id, idempotency_key) DO NOTHING
                """,
                actorId,
                key,
                requestHash);

        if (inserted == 0) {
            IdempotencyRecord existing = idempotencyRepository
                    .findByActorIdAndIdempotencyKey(actorId, key)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Could not resolve the repeated request; retry it"));

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This Idempotency-Key was already used with different request data");
            }

            if (existing.getWorkItemId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The original request is not complete; retry shortly");
            }

            WorkItem original = workItemRepository
                    .findById(existing.getWorkItemId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "The original work item no longer exists"));

            return WorkItemResponse.from(original);
        }

        WorkItemResponse created = workItemService.createWorkItem(
                actorId,
                request.getTeam(),
                request.getTitle(),
                request.getDescription(),
                request.getType(),
                request.getPriority(),
                request.getParentId());

        jdbcTemplate.update("""
                UPDATE idempotency_records
                SET work_item_id = ?
                WHERE actor_id = ? AND idempotency_key = ?
                """,
                created.getId(),
                actorId,
                key);

        return created;
    }

    private String hashRequest(CreateWorkItemRequest request) {
        String requestData = String.join("|",
                String.valueOf(request.getTeam()),
                String.valueOf(request.getTitle()),
                String.valueOf(request.getDescription()),
                String.valueOf(request.getType()),
                String.valueOf(request.getPriority()),
                String.valueOf(request.getParentId()));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    requestData.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Could not calculate the request fingerprint",
                    exception);
        }
    }
}
