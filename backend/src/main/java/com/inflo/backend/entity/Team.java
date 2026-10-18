package com.inflo.backend.entity;

import com.inflo.backend.enums.TeamType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 40)
    private TeamType name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Team() {}

    public Team(TeamType name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public TeamType getName() {
        return name;
    }

    public void setName(TeamType name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
