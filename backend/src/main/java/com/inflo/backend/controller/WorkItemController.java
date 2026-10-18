
package com.inflo.backend.controller;

import java.util.List;

import com.inflo.backend.dto.ActivityLogResponse;
import com.inflo.backend.dto.AssignWorkItemRequest;
import com.inflo.backend.dto.AssignmentHistoryResponse;
import com.inflo.backend.dto.CreateWorkItemRequest;
import com.inflo.backend.dto.UpdateWorkItemRequest;
import com.inflo.backend.dto.WorkItemResponse;
import com.inflo.backend.entity.ActivityLog;
import com.inflo.backend.entity.User;
import com.inflo.backend.entity.WorkItem;
import com.inflo.backend.enums.Priority;
import com.inflo.backend.enums.UserRole;
import com.inflo.backend.enums.WorkItemStatus;
import com.inflo.backend.repository.ActivityLogRepository;
import com.inflo.backend.repository.AssignmentHistoryRepository;
import com.inflo.backend.repository.UserRepository;
import com.inflo.backend.repository.WorkItemRepository;
import com.inflo.backend.service.IdempotentWorkItemCreationService;
import com.inflo.backend.service.WorkItemService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/work-items")
public class WorkItemController {

    private final WorkItemService workItemService;
    private final IdempotentWorkItemCreationService idempotentCreationService;
    private final UserRepository userRepository;
    private final AssignmentHistoryRepository assignmentHistoryRepository;
    private final WorkItemRepository workItemRepository;
    private final ActivityLogRepository activityLogRepository;

    public WorkItemController(
            WorkItemService workItemService,
            IdempotentWorkItemCreationService idempotentCreationService,
            UserRepository userRepository,
            AssignmentHistoryRepository assignmentHistoryRepository,
            WorkItemRepository workItemRepository,
            ActivityLogRepository activityLogRepository) {

        this.workItemService = workItemService;
        this.idempotentCreationService = idempotentCreationService;
        this.userRepository = userRepository;
        this.assignmentHistoryRepository = assignmentHistoryRepository;
        this.workItemRepository = workItemRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @GetMapping
    public Page<WorkItemResponse> getWorkItems(
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) WorkItemStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePagination(page, size);

        if (search != null && search.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "search cannot exceed 100 characters");
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return workItemService.getWorkItems(
                teamId,
                assigneeId,
                status,
                priority,
                search,
                pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItemResponse createWorkItem(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateWorkItemRequest request) {

        Long actorId = getAuthenticatedUserId(authentication);

        return idempotentCreationService.create(
                actorId,
                idempotencyKey,
                request);
    }

    @PutMapping("/{id}")
    public WorkItemResponse updateWorkItem(
            @PathVariable("id") Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateWorkItemRequest request) {

        Long actorId = getAuthenticatedUserId(authentication);

        return workItemService.updateWorkItem(
        actorId,
        id,
        request.getStatus(),
        request.getPriority(),
        request.getVersion());
    }

    @PutMapping("/{id}/assign")
    public WorkItemResponse assignWorkItem(
            @PathVariable("id") Long id,
            Authentication authentication,
            @Valid @RequestBody AssignWorkItemRequest request) {

        Long actorId = getAuthenticatedUserId(authentication);

        return workItemService.assignWorkItem(
                actorId,
                id,
                request.getAssigneeId());
    }

    @GetMapping("/{id}/assignment-history")
    @Transactional(readOnly = true)
    public List<AssignmentHistoryResponse> getAssignmentHistory(
            @PathVariable("id") Long id,
            Authentication authentication) {

        Long actorId = getAuthenticatedUserId(authentication);

        User actor = userRepository.findById(actorId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found or inactive"));

        WorkItem item = workItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Work item not found"));

        checkActivityAccess(actor, item);

        return assignmentHistoryRepository
                .findHistoryByWorkItemId(id)
                .stream()
                .map(AssignmentHistoryResponse::from)
                .toList();
    }

    @GetMapping("/{id}/activity")
    @Transactional(readOnly = true)
    public Page<ActivityLogResponse> getWorkItemActivity(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        validatePagination(page, size);

        Long actorId = getAuthenticatedUserId(authentication);

        User actor = userRepository.findById(actorId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found or inactive"));

        WorkItem item = workItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Work item not found"));

        checkActivityAccess(actor, item);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("occurredAt"),
                        Sort.Order.desc("id")));

        Page<ActivityLog> logs =
                activityLogRepository
                        .findByWorkItemIdOrderByOccurredAtDesc(id, pageable);

        return logs.map(ActivityLogResponse::from);
    }

    private void checkActivityAccess(User actor, WorkItem item) {

        // Managers can view work-item history across all teams.
        if (actor.getRole() == UserRole.MANAGER) {
            return;
        }

        // Other roles must belong to the work item's team.
        if (actor.getTeam() == null
                || item.getTeam() == null
                || !actor.getTeam().getId().equals(item.getTeam().getId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot view history for another team's work items");
        }

        // Members can view history only for work assigned to them.
        if (actor.getRole() == UserRole.MEMBER
                && (item.getAssignee() == null
                || !item.getAssignee().getId().equals(actor.getId()))) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only view history for work items assigned to you");
        }
    }

    private void validatePagination(int page, int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page cannot be negative");
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and 100");
        }
    }

    private Long getAuthenticatedUserId(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User account not found or inactive"));

        return user.getId();
    }
}
