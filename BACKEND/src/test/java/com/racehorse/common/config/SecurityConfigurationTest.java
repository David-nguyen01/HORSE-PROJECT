package com.racehorse.common.config;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigurationTest {
    @Test
    void readsCurrentDatabaseRolesInsteadOfStaleJwtRoleClaim() {
        UUID userId = UUID.randomUUID();
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount currentUser = new UserAccount("trainer", "trainer@example.com", "hash", Set.of(Role.HEAD_TRAINER));
        when(repository.findById(userId)).thenReturn(Optional.of(currentUser));
        Jwt staleJwt = Jwt.withTokenValue("token").subject(userId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("roles", Set.of("STABLE_STAFF")).header("alg", "none").build();

        var authentication = new SecurityConfiguration().jwtAuthenticationConverter(repository).convert(staleJwt);

        assertThat(authentication.getAuthorities()).extracting("authority")
                .contains("ROLE_HEAD_TRAINER")
                .doesNotContain("ROLE_STABLE_STAFF");
    }
}
