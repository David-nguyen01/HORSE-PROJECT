package com.racehorse.medical.controller;

import com.racehorse.medical.dto.RequestFollowUpHandover;
import com.racehorse.medical.service.FollowUpService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/medical/follow-ups")
@PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_VIEW')")
public class FollowUpController {
    private final FollowUpService service;

    public FollowUpController(FollowUpService service) { this.service = service; }

    @GetMapping
    public List<FollowUpService.FollowUpView> list() { return service.listOpen(); }

    @PostMapping("/{id}/handover")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public FollowUpService.FollowUpView handover(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody RequestFollowUpHandover request) {
        return service.requestHandover(id, UUID.fromString(jwt.getSubject()), request.reason(), request.mode());
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public FollowUpService.FollowUpView accept(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.acceptHandover(id, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{id}/handover/cancel")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public FollowUpService.FollowUpView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.cancelHandover(id, UUID.fromString(jwt.getSubject()));
    }
}
