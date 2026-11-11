package com.racehorse.caretask.controller;

import com.racehorse.caretask.dto.*;
import com.racehorse.caretask.service.CareRoutineDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/care-routine-definitions")
public class CareRoutineDefinitionController {

    private final CareRoutineDefinitionService service;

    public CareRoutineDefinitionController(CareRoutineDefinitionService service) {
        this.service = service;
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('PERM_STABLE_CARE_VIEW')")
    public List<CareRoutineDefinitionResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/reserve")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_VIEW')")
    public List<CareRoutineDefinitionResponse> listReserve() {
        return service.listReserve();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_STABLE_CARE_VIEW')")
    public CareRoutineDefinitionResponse get(
            @PathVariable UUID id,
            Authentication authentication) {
        CareRoutineDefinitionResponse def = service.get(id);
        if (!def.isActive() && !isClubManager(authentication)) {
            throw new AccessDeniedException("Chỉ Quản lý CLB (Club Manager) mới có quyền truy cập định nghĩa trong Thư viện dự phòng");
        }
        return def;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public CareRoutineDefinitionResponse create(
            @Valid @RequestBody CreateCareRoutineDefinitionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.createInReserve(actorId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public CareRoutineDefinitionResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCareRoutineDefinitionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.update(id, actorId, request);
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public CareRoutineDefinitionResponse transfer(
            @PathVariable UUID id,
            @Valid @RequestBody TransferCareRoutineRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.transfer(id, actorId, request.active());
    }

    @PostMapping("/{id}/move-to-active")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public CareRoutineDefinitionResponse moveToActive(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.transfer(id, actorId, true);
    }

    @PostMapping("/{id}/move-to-reserve")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public CareRoutineDefinitionResponse moveToReserve(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.transfer(id, actorId, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        service.delete(id, actorId);
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public List<CareRoutineDefinitionResponse> reorder(
            @Valid @RequestBody ReorderCareRoutinesRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.reorder(actorId, request.orderedIds());
    }

    private boolean isClubManager(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CLUB_MANAGER".equals(a.getAuthority()));
    }

    private UUID getActorId(Jwt jwt, Authentication authentication) {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {}
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {}
        }
        return UUID.randomUUID();
    }
}
