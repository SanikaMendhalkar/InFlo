
package com.inflo.backend.service;

import java.time.Instant;
import java.util.List;
import com.inflo.backend.repository.AssignmentHistoryRepository;
import com.inflo.backend.dto.WorkItemResponse;
import com.inflo.backend.entity.ActivityLog;
import com.inflo.backend.entity.Team;
import com.inflo.backend.entity.User;
import com.inflo.backend.entity.WorkItem;
import com.inflo.backend.enums.ActivityAction;
import com.inflo.backend.enums.Priority;
import com.inflo.backend.enums.TeamType;
import com.inflo.backend.enums.UserRole;
import com.inflo.backend.enums.WorkItemStatus;
import com.inflo.backend.enums.WorkItemType;
import com.inflo.backend.repository.ActivityLogRepository;
import com.inflo.backend.repository.TeamRepository;
import com.inflo.backend.repository.UserRepository;
import com.inflo.backend.repository.WorkItemRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkItemService {

    private final WorkItemRepository workItemRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AssignmentHistoryRepository assignmentHistoryRepository;

  public WorkItemService(
        WorkItemRepository workItemRepository,
        UserRepository userRepository,
        TeamRepository teamRepository,
        ActivityLogRepository activityLogRepository,
        AssignmentHistoryRepository assignmentHistoryRepository) {

    this.workItemRepository = workItemRepository;
    this.userRepository = userRepository;
    this.teamRepository = teamRepository;
    this.activityLogRepository = activityLogRepository;
    this.assignmentHistoryRepository = assignmentHistoryRepository;
}

    // List only work items visible to the authenticated user.
    @Transactional(readOnly = true)
    public Page<WorkItemResponse> getWorkItems(
            Long teamId,
            Long assigneeId,
            WorkItemStatus status,
            Priority priority,
            String search,
            Pageable pageable) {

        User actor = getAuthenticatedUser();

        Long visibleTeamId = null;
        Long effectiveAssigneeId = assigneeId;

        if (actor.getRole() != UserRole.MANAGER) {
            if (actor.getTeam() == null) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account is not assigned to a team");
            }

            visibleTeamId = actor.getTeam().getId();
        }

        // Members can only see their own assigned work.
        if (actor.getRole() == UserRole.MEMBER) {
            effectiveAssigneeId = actor.getId();
        }

        Page<WorkItem> results = workItemRepository.searchWorkItems(
                visibleTeamId,
                teamId,
                effectiveAssigneeId,
                status,
                priority,
                search == null ? "" : search.trim(),
                pageable);

        return results.map(WorkItemResponse::from);
    }

    // Assign work to a member of the work item's team.
    @Transactional
    public WorkItemResponse assignWorkItem(
            Long actorId,
            Long workItemId,
            Long assigneeId) {

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));

        if (!actor.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Inactive users cannot assign work");
        }

        if (actor.getRole() == UserRole.MEMBER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Members cannot reassign work items");
        }

        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Work item not found"));

        // Managers can assign across teams.
        // Team Leads can assign only within their own team.
        checkTeamPermission(actor, item.getTeam());

        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Assignee not found"));

        if (!assignee.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Assignee is inactive");
        }

        if (assignee.getRole() != UserRole.MEMBER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Work items can only be assigned to team members");
        }

        if (assignee.getTeam() == null
                || !assignee.getTeam().getId()
                        .equals(item.getTeam().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Assignee must belong to the work item's team");
        }

        User previousAssignee = item.getAssignee();

        item.setAssignee(assignee);
        item.setAssignedBy(actor);
        item.setAssignedAt(Instant.now());
        item.setUpdatedBy(actor);

        WorkItem saved = workItemRepository.saveAndFlush(item);

       if (previousAssignee == null
        || !previousAssignee.getId().equals(assignee.getId())) {

    assignmentHistoryRepository.save(
            new com.inflo.backend.entity.AssignmentHistory(
                    saved,
                    previousAssignee,
                    assignee,
                    actor,
                    Instant.now()));

    logActivity(
            saved,
            actor,
            ActivityAction.ASSIGNED,
            "Assigned to " + assignee.getFullName());
}

        return WorkItemResponse.from(saved);
    }

    // Create a new work item.
    @Transactional
    public WorkItemResponse createWorkItem(
            Long actorId,
            TeamType teamType,
            String title,
            String description,
            WorkItemType type,
            Priority priority,
            Long parentId) {

        User actor = userRepository.findById(actorId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Actor not found: " + actorId));

        if (!actor.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Inactive users cannot create work items");
        }

        if (actor.getRole() == UserRole.MEMBER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Members cannot create work items");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }

        Team team = teamRepository.findByName(teamType)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Team not found: " + teamType));

        checkTeamPermission(actor, team);

        WorkItem parent = null;

        if (parentId != null) {
            parent = workItemRepository.findById(parentId)
                    .orElseThrow(() ->
                            new EntityNotFoundException(
                                    "Parent work item not found"));

            if (!parent.getTeam().getId().equals(team.getId())) {
                throw new IllegalArgumentException(
                        "A subtask and its parent must belong to the same team");
            }
        }

        WorkItem item = new WorkItem();
        item.setTitle(title.trim());
        item.setDescription(description.trim());
        item.setType(type == null ? WorkItemType.ISSUE : type);
        item.setPriority(priority == null ? Priority.MEDIUM : priority);
        item.setStatus(WorkItemStatus.OPEN);
        item.setTeam(team);
        item.setCreatedBy(actor);
        item.setUpdatedBy(actor);
        item.setParent(parent);

        User assignee = chooseLeastLoadedMember(team.getId());

        item.setAssignee(assignee);
        item.setAssignedBy(actor);
        item.setAssignedAt(Instant.now());

        WorkItem saved = workItemRepository.saveAndFlush(item);

        logActivity(
                saved,
                actor,
                ActivityAction.CREATED,
                "Work item created");

       if (assignee != null) {
    assignmentHistoryRepository.save(
            new com.inflo.backend.entity.AssignmentHistory(
                    saved,
                    null,
                    assignee,
                    actor,
                    Instant.now()));

    logActivity(
            saved,
            actor,
            ActivityAction.ASSIGNED,
            "Initially assigned to " + assignee.getFullName());
}

        return WorkItemResponse.from(saved);
    }

    // Update a work item's status and priority.
    @Transactional
   public WorkItemResponse updateWorkItem(
        Long actorId,
        Long workItemId,
        WorkItemStatus newStatus,
        Priority newPriority,
        Long expectedVersion) {

        User actor = userRepository.findById(actorId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Actor not found: " + actorId));

        if (!actor.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Inactive users cannot update work items");
        }

        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Work item not found: " + workItemId));

        checkTeamPermission(actor, item.getTeam());
if (expectedVersion == null) {
    throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Version is required for concurrency control");
}

if (!expectedVersion.equals(item.getVersion())) {
    throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "This work item has been updated by another user. "
                    + "Refresh it and retry your changes.");
}
        if (actor.getRole() == UserRole.MEMBER) {
            if (item.getAssignee() == null
                    || !item.getAssignee().getId().equals(actor.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Members can update only their assigned work items");
            }

            if (newPriority != null
                    && newPriority != item.getPriority()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Members cannot change work item priority");
            }
        }

        if (newStatus == null || newPriority == null) {
            throw new IllegalArgumentException(
                    "Both status and priority are required");
        }

        if (item.getStatus() == WorkItemStatus.CLOSED
                && newStatus != WorkItemStatus.CLOSED) {
            throw new IllegalArgumentException(
                    "A closed work item cannot be reopened");
        }

        WorkItemStatus oldStatus = item.getStatus();
        Priority oldPriority = item.getPriority();

        item.setStatus(newStatus);
        item.setPriority(newPriority);
        item.setUpdatedBy(actor);

        if (newStatus == WorkItemStatus.RESOLVED
                && oldStatus != WorkItemStatus.RESOLVED) {
            item.setResolvedAt(Instant.now());
            item.setResolvedBy(actor);
        }

        if (newStatus != WorkItemStatus.RESOLVED
                && oldStatus == WorkItemStatus.RESOLVED) {
            item.setResolvedAt(null);
            item.setResolvedBy(null);
        }

        WorkItem saved = workItemRepository.saveAndFlush(item);

        if (oldStatus != newStatus) {
            logActivity(
                    saved,
                    actor,
                    ActivityAction.STATUS_CHANGED,
                    "Status changed from " + oldStatus + " to " + newStatus);
        }

        if (oldPriority != newPriority) {
            logActivity(
                    saved,
                    actor,
                    ActivityAction.PRIORITY_CHANGED,
                    "Priority changed from " + oldPriority + " to " + newPriority);
        }

        if (newStatus == WorkItemStatus.RESOLVED
                && oldStatus != WorkItemStatus.RESOLVED) {
            logActivity(
                    saved, actor, ActivityAction.RESOLVED, "Work item resolved");
        }

        if (newStatus == WorkItemStatus.CLOSED
                && oldStatus != WorkItemStatus.CLOSED) {
            logActivity(
                    saved, actor, ActivityAction.CLOSED, "Work item closed");
        }

        return WorkItemResponse.from(saved);
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        return userRepository.findByEmail(authentication.getName())
                .filter(User::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User account not found or inactive"));
    }

    private void checkTeamPermission(User actor, Team team) {
        if (actor.getRole() == UserRole.MANAGER) {
            return;
        }

        if (actor.getTeam() == null
                || !actor.getTeam().getId().equals(team.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to access this team's work items");
        }
    }

    private User chooseLeastLoadedMember(Long teamId) {
        List<User> members =
                userRepository.findByTeamIdAndRoleAndActiveTrue(
                        teamId, UserRole.MEMBER);

        if (members.isEmpty()) {
            throw new IllegalStateException(
                    "No active team members are available for assignment");
        }

        User leastLoaded = null;
        long minimumLoad = Long.MAX_VALUE;

        for (User member : members) {
            long load = workItemRepository.countByAssigneeIdAndStatusIn(
                    member.getId(),
                    List.of(
                            WorkItemStatus.OPEN,
                            WorkItemStatus.IN_PROGRESS,
                            WorkItemStatus.BLOCKED));

            if (leastLoaded == null
                    || load < minimumLoad
                    || (load == minimumLoad
                    && member.getId() < leastLoaded.getId())) {
                leastLoaded = member;
                minimumLoad = load;
            }
        }

        return leastLoaded;
    }

    private void logActivity(
            WorkItem item,
            User actor,
            ActivityAction action,
            String details) {

        ActivityLog log = new ActivityLog(
                item,
                actor,
                action,
                details);

        activityLogRepository.save(log);
    }
}
