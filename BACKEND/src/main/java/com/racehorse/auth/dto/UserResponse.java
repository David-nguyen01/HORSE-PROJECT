package com.racehorse.auth.dto;

import com.racehorse.auth.entity.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean enabled,
        Set<Role> roles,
        Instant createdAt
) {
}
