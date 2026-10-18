package com.inflo.backend.repository;

import com.inflo.backend.entity.Team;
import com.inflo.backend.enums.TeamType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByName(TeamType name);
}
