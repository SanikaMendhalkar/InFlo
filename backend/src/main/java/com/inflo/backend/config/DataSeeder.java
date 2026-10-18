
package com.inflo.backend.config;

import com.inflo.backend.entity.Team;
import com.inflo.backend.entity.User;
import com.inflo.backend.enums.TeamType;
import com.inflo.backend.enums.UserRole;
import com.inflo.backend.repository.TeamRepository;
import com.inflo.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedInitialData(
            TeamRepository teamRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> seed(teamRepository, userRepository, passwordEncoder);
    }

    @Transactional
    void seed(
            TeamRepository teamRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        String passwordHash = passwordEncoder.encode("InFlo@123");

        for (TeamType teamType : TeamType.values()) {
            Team team = teamRepository.findByName(teamType)
                    .orElseGet(() -> teamRepository.save(new Team(teamType)));

            String teamPrefix = teamType.name().toLowerCase()
                    .replace("_", ".");

            String leadEmail = teamPrefix + ".lead@inflo.local";

            User lead = userRepository.findByEmail(leadEmail)
                    .orElseGet(() -> userRepository.save(new User(
                            teamType.name().replace("_", " ") + " Lead",
                            leadEmail,
                            UserRole.TEAM_LEAD,
                            team
                    )));

            initializePassword(lead, passwordHash, userRepository);

            for (int i = 1; i <= 10; i++) {
                String email = teamPrefix + ".member" + i + "@inflo.local";

               User member = userRepository.findByEmail(email).orElse(null);

if (member == null) {
    member = userRepository.save(new User(
            teamType.name().replace("_", " ") + " Member " + i,
            email,
            UserRole.MEMBER,
            team
    ));
}

                initializePassword(member, passwordHash, userRepository);
            }
        }

        User manager = userRepository.findByEmail("manager@inflo.local")
                .orElseGet(() -> userRepository.save(new User(
                        "InFlo Manager",
                        "manager@inflo.local",
                        UserRole.MANAGER,
                        null
                )));

        initializePassword(manager, passwordHash, userRepository);
    }

    private void initializePassword(
            User user,
            String passwordHash,
            UserRepository userRepository) {

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.setPasswordHash(passwordHash);
            userRepository.save(user);
        }
    }
}
