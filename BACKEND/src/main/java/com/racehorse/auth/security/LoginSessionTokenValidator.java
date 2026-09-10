package com.racehorse.auth.security;

import com.racehorse.auth.repository.LoginSessionRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.UUID;

public class LoginSessionTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SESSION = new OAuth2Error(
            "invalid_token",
            "Phiên đăng nhập không tồn tại, đã hết hạn hoặc đã đăng xuất",
            null);

    private final LoginSessionRepository sessionRepository;
    private final Clock clock;

    public LoginSessionTokenValidator(LoginSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String sessionId = jwt.getClaimAsString("sid");
        if (sessionId == null) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }
        try {
            boolean active = sessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter(
                    UUID.fromString(sessionId),
                    clock.instant());
            return active
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        } catch (IllegalArgumentException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }
    }
}
