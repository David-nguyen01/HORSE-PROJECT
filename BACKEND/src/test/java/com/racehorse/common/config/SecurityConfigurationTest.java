package com.racehorse.common.config;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigurationTest {
    @Test
    void managerHasAllPermissionsWithoutDependingOnMatrixRows() {
        UUID userId = UUID.randomUUID();
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount manager = new UserAccount("admin", "admin@example.com", "hash", Set.of(Role.CLUB_MANAGER));
        when(repository.findById(userId)).thenReturn(Optional.of(manager));
        Jwt jwt = Jwt.withTokenValue("token").subject(userId.toString())
                .header("alg", "none").build();
        var authentication = new SecurityConfiguration().jwtAuthenticationConverter(repository).convert(jwt);
        assertThat(authentication.getAuthorities()).extracting("authority")
                .contains("ROLE_CLUB_MANAGER", "PERM_PROFILE_APPROVE", "PERM_INVENTORY_APPROVE",
                        "PERM_MEDICAL_EDIT", "PERM_SYSTEM_EDIT", "PERM_TRAINING_EDIT")
                .doesNotContain("ROLE_HORSE_OWNER", "ROLE_STABLE_STAFF", "PERM_RACING_VIEW");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findGrantedPermissionCodes(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsPrivateNetworkAccessOnlyFromConfiguredFrontend() throws IOException {
        var source = new SecurityConfiguration().corsConfigurationSource(
                new CorsProperties(List.of("https://horse-project-phi.vercel.app")));
        var request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader("Origin", "https://horse-project-phi.vercel.app");
        var configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowPrivateNetwork()).isTrue();
        assertThat(configuration.checkOrigin("https://horse-project-phi.vercel.app"))
                .isEqualTo("https://horse-project-phi.vercel.app");
        assertThat(configuration.checkOrigin("https://unrelated.vercel.app")).isNull();

        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Private-Network", "true");
        var response = new MockHttpServletResponse();
        assertThat(new DefaultCorsProcessor().processRequest(configuration, request, response)).isTrue();
        assertThat(response.getHeader("Access-Control-Allow-Private-Network")).isEqualTo("true");
    }

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
