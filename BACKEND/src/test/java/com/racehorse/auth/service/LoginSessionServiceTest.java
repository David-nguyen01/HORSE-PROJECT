package com.racehorse.auth.service;

import com.racehorse.auth.config.JwtProperties;
import com.racehorse.auth.entity.LoginSession;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.LoginSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Mock
    private LoginSessionRepository repository;

    private LoginSessionService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "racehorse-backend",
                "unused-in-this-test",
                Duration.ofMinutes(15),
                Duration.ofDays(30));
        service = new LoginSessionService(
                repository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createStoresOnlyRefreshTokenHashAndClientMetadata() {
        UserAccount user = new UserAccount(
                "owner.one",
                "owner@example.com",
                "encoded-password",
                Set.of(Role.HORSE_OWNER));

        IssuedLoginSession issued = service.create(
                user,
                new ClientContext("127.0.0.1", "Racehorse Web Test"));

        ArgumentCaptor<LoginSession> captor = ArgumentCaptor.forClass(LoginSession.class);
        verify(repository).saveAndFlush(captor.capture());
        LoginSession stored = captor.getValue();
        assertThat(stored.getRefreshTokenHash()).hasSize(64);
        assertThat(stored.getRefreshTokenHash()).isNotEqualTo(issued.refreshToken());
        assertThat(stored.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(stored.getUserAgent()).isEqualTo("Racehorse Web Test");
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }
}
