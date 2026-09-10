package com.racehorse.auth.dto;

import com.racehorse.auth.entity.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserSummary user
) {
    public record UserSummary(UUID id, String username, String email, Set<Role> roles) {
    }
}
