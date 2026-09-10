package com.racehorse.auth.service;

import com.racehorse.auth.config.JwtProperties;
import com.racehorse.auth.entity.LoginSession;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.LoginSessionRepository;
import com.racehorse.common.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class LoginSessionService {

    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LoginSessionRepository repository;
    private final JwtProperties properties;
    private final Clock clock;

    public LoginSessionService(
            LoginSessionRepository repository,
            JwtProperties properties,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedLoginSession create(UserAccount user, ClientContext client) {
        String rawToken = generateToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        LoginSession session = new LoginSession(
                user,
                hash(rawToken),
                truncate(client.ipAddress(), MAX_IP_LENGTH),
                truncate(client.userAgent(), MAX_USER_AGENT_LENGTH),
                now,
                expiresAt);
        repository.saveAndFlush(session);
        return new IssuedLoginSession(session.getId(), user, rawToken, expiresAt);
    }

    public IssuedLoginSession rotate(String rawToken, ClientContext client) {
        Instant now = clock.instant();
        LoginSession session = repository.findByRefreshTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token không hợp lệ"));
        if (!session.isUsableAt(now)) {
            throw new UnauthorizedException("Phiên đăng nhập đã hết hạn hoặc bị thu hồi");
        }

        String replacement = generateToken();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        session.rotate(
                hash(replacement),
                truncate(client.ipAddress(), MAX_IP_LENGTH),
                truncate(client.userAgent(), MAX_USER_AGENT_LENGTH),
                now,
                expiresAt);
        return new IssuedLoginSession(
                session.getId(),
                session.getUser(),
                replacement,
                expiresAt);
    }

    public void revoke(String rawToken) {
        repository.findByRefreshTokenHash(hash(rawToken))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    private String generateToken() {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không được hỗ trợ", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
