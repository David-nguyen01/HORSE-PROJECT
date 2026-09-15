package com.racehorse.horse.controller;

import com.racehorse.horse.dto.PedigreeResponse;
import com.racehorse.horse.service.PedigreeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}/pedigree")
public class PedigreeController {

    private final PedigreeService pedigreeService;

    public PedigreeController(PedigreeService pedigreeService) {
        this.pedigreeService = pedigreeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER')")
    PedigreeResponse get(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        boolean privileged = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_CLUB_MANAGER") || authority.getAuthority().equals("ROLE_HEAD_TRAINER"));
        return pedigreeService.get(horseId, UUID.fromString(jwt.getSubject()), privileged);
    }
}
