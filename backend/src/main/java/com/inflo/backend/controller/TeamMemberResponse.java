
package com.inflo.backend.controller;

public record TeamMemberResponse(
        Long id,
        String fullName,
        String email,
        String role,
        String teamName,
        boolean active,
        long activeWorkItems
) {}
