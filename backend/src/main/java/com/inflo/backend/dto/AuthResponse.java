
package com.inflo.backend.dto;

public record AuthResponse(
    String token,
    Long userId,
    String fullName,
    String email,
    String role,
    String team
) {}

