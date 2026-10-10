package com.racehorse.horse.service;

import com.racehorse.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OwnerHorseReadGuard {
    private final HorseAccessService horseAccess;

    public OwnerHorseReadGuard(HorseAccessService horseAccess) {
        this.horseAccess = horseAccess;
    }

    public void requireHorse(UUID horseId, Jwt jwt, Authentication authentication) {
        if (isOwner(authentication)) {
            if (horseId == null) {
                throw new BusinessException("Chủ ngựa phải chọn ngựa để xem dữ liệu chăm sóc");
            }
            horseAccess.requireReadable(horseId, UUID.fromString(jwt.getSubject()), false);
        }
    }

    public boolean isOwner(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_HORSE_OWNER".equals(authority.getAuthority()));
    }
}
