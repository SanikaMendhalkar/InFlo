
package com.inflo.backend.controller;

import com.inflo.backend.entity.Team;
import com.inflo.backend.entity.User;
import com.inflo.backend.enums.UserRole;
import com.inflo.backend.repository.TeamRepository;
import com.inflo.backend.repository.UserRepository;

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
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public TeamController(
            TeamRepository teamRepository,
            UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TeamResponse> getTeams() {
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

        List<Team> visibleTeams;

        if (actor.getRole() == UserRole.MANAGER) {
            visibleTeams = teamRepository.findAll();
        } else {
            if (actor.getTeam() == null) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account is not assigned to a team");
            }

            visibleTeams = List.of(actor.getTeam());
        }

        return visibleTeams.stream()
                .map(team -> new TeamResponse(
                        team.getId(),
                        team.getName().name()))
                .toList();
    }

    public record TeamResponse(Long id, String name) {}
}
