package com.racehorse.horse.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.entity.Horse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OwnerHorseReadGuardTest {
    private final HorseAccessService horseAccess = mock(HorseAccessService.class);
    private final OwnerHorseReadGuard guard = new OwnerHorseReadGuard(horseAccess);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();
    private final Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
            java.util.Map.of("alg", "none"), java.util.Map.of("sub", ownerId.toString()));

    @Test
    void ownerMustSelectHorseForCollectionReads() {
        assertThatThrownBy(() -> guard.requireHorse(null, jwt, authentication("ROLE_HORSE_OWNER")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(horseAccess);
    }

    @Test
    void ownerCanOnlyReadOwnedHorse() {
        guard.requireHorse(horseId, jwt, authentication("ROLE_HORSE_OWNER"));
        verify(horseAccess).requireReadable(horseId, ownerId, false);
    }

    @Test
    void staffReadDoesNotRequireOwnership() {
        guard.requireHorse(null, jwt, authentication("ROLE_STABLE_STAFF"));
        verifyNoInteractions(horseAccess);
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken("user", "n/a",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
