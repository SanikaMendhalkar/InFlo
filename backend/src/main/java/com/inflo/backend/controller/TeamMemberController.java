
package com.inflo.backend.controller;

import com.inflo.backend.entity.Team;
import com.inflo.backend.entity.User;
import com.inflo.backend.enums.UserRole;
import com.inflo.backend.enums.WorkItemStatus;
import com.inflo.backend.repository.TeamRepository;
import com.inflo.backend.repository.UserRepository;
import com.inflo.backend.repository.WorkItemRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/teams/members")
public class TeamMemberController {

    private static final List<WorkItemStatus> ACTIVE_STATUSES =
            List.of(
                    WorkItemStatus.OPEN,
                    WorkItemStatus.IN_PROGRESS,
                    WorkItemStatus.BLOCKED
            );

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final WorkItemRepository workItemRepository;

    public TeamMemberController(
            UserRepository userRepository,
            TeamRepository teamRepository,
            WorkItemRepository workItemRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.workItemRepository = workItemRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        User actor = userRepository.findByEmail(authentication.getName())
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User account not found or inactive"));

        List<User> members;

        if (actor.getRole() == UserRole.MANAGER) {
            members = userRepository.findAll().stream()
                    .filter(User::isActive)
                    .toList();
        } else if (actor.getRole() == UserRole.TEAM_LEAD) {
            if (actor.getTeam() == null) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account is not assigned to a team");
            }

            members = userRepository
                    .findByTeamIdAndRoleAndActiveTrue(
                            actor.getTeam().getId(),
                            UserRole.MEMBER);
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only managers and team leads can view team members");
        }

        return members.stream()
                .map(member -> {
                    Team team = member.getTeam();

                    long activeWorkItems =
                            workItemRepository.countByAssigneeIdAndStatusIn(
                                    member.getId(),
                                    ACTIVE_STATUSES);

                    return new TeamMemberResponse(
                            member.getId(),
                            member.getFullName(),
                            member.getEmail(),
                            member.getRole().name(),
                            team != null ? team.getName().name() : null,
                            member.isActive(),
                            activeWorkItems
                    );
                })
                .toList();
    }
}
