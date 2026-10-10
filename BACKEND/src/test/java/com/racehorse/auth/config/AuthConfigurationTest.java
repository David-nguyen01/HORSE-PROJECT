package com.racehorse.auth.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthConfigurationTest {
    private final AuthConfiguration configuration = new AuthConfiguration();

    @Test
    void requiresExplicitNonPublicJwtSecret() {
        assertThatThrownBy(() -> configuration.jwtSecretKey(properties(null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> configuration.jwtSecretKey(properties("")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> configuration.jwtSecretKey(properties(
                "cmFjZWhvcnNlLWRldmVsb3BtZW50LWtleS1jaGFuZ2UtbWUtbm93")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đã công khai");
    }

    @Test
    void acceptsNewBase64KeyOfAtLeastThirtyTwoBytes() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        assertThat(configuration.jwtSecretKey(properties(secret)).getEncoded()).hasSize(32);
    }

    private JwtProperties properties(String secret) {
        return new JwtProperties("racehorse-backend", secret, Duration.ofMinutes(15), Duration.ofDays(30));
    }
}
