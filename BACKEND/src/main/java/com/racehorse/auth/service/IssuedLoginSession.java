package com.racehorse.auth.service;

import com.racehorse.auth.entity.UserAccount;

import java.time.Instant;
import java.util.UUID;

record IssuedLoginSession(
        UUID sessionId,
        UserAccount user,
        String refreshToken,
        Instant expiresAt
) {
}
