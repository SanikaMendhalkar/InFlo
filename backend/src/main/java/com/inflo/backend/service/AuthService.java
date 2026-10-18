
package com.inflo.backend.service;

import com.inflo.backend.dto.AuthResponse;
import com.inflo.backend.entity.User;
import com.inflo.backend.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {

        User user = userRepository.findByEmail(
                        email == null ? "" : email.trim().toLowerCase())
                .filter(User::isActive)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        String team = user.getTeam() == null
                ? null
                : user.getTeam().getName().name();

        return new AuthResponse(
                token,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                team
        );
    }
}
