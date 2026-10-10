package com.racehorse.auth.security;

import com.racehorse.auth.repository.LoginSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoginSessionTokenValidatorTest {
    private final LoginSessionRepository sessions = mock(LoginSessionRepository.class);
    private final Instant now = Instant.parse("2026-10-09T00:00:00Z");
    private final LoginSessionTokenValidator validator = new LoginSessionTokenValidator(
            sessions, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void acceptsOnlyAnActiveSessionBelongingToTheTokenSubject() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(sessions.existsByIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, userId, now))
                .thenReturn(true);

        assertThat(validator.validate(token(userId.toString(), sessionId.toString())).hasErrors()).isFalse();
        verify(sessions).existsByIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, userId, now);
    }

    @Test
    void rejectsSessionOwnedByAnotherAccount() {
        UUID sessionId = UUID.randomUUID();
        UUID forgedSubject = UUID.randomUUID();

        assertThat(validator.validate(token(forgedSubject.toString(), sessionId.toString())).hasErrors()).isTrue();
        verify(sessions).existsByIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, forgedSubject, now);
    }

    @Test
    void rejectsMalformedOrMissingSessionAndSubject() {
        assertThat(validator.validate(token(UUID.randomUUID().toString(), "not-a-uuid")).hasErrors()).isTrue();
        assertThat(validator.validate(token("not-a-uuid", UUID.randomUUID().toString())).hasErrors()).isTrue();
        assertThat(validator.validate(Jwt.withTokenValue("test").header("alg", "HS256")
                .subject(UUID.randomUUID().toString()).build()).hasErrors()).isTrue();
        verifyNoInteractions(sessions);
    }

    private Jwt token(String subject, String sid) {
        return Jwt.withTokenValue("test").header("alg", "HS256")
                .subject(subject).claim("sid", sid).build();
    }
}
