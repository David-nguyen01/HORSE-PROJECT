package com.racehorse.horse.controller;

import com.racehorse.horse.dto.CreateHorseRequest;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.dto.ReplaceOwnershipRequest;
import com.racehorse.horse.dto.UpdateHorseRequest;
import com.racehorse.horse.dto.UpdateOwnedHorseRequest;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.auth.entity.Role;
import com.racehorse.horse.service.HorseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses")
public class HorseController {

    private final HorseService horseService;

    public HorseController(HorseService horseService) {
        this.horseService = horseService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    ResponseEntity<HorseResponse> create(@Valid @RequestBody CreateHorseRequest request) {
        HorseResponse response = horseService.create(request);
        return ResponseEntity
                .created(URI.create("/api/horses/" + response.id()))
                .body(response);
    }

    @PostMapping("/mine")
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasRole('HORSE_OWNER')")
    ResponseEntity<HorseResponse> createForOwner(@Valid @RequestBody CreateHorseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        HorseResponse response = horseService.createForOwner(request, userId(jwt));
        return ResponseEntity.created(URI.create("/api/horses/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','STABLE_STAFF')")
    Page<HorseResponse> search(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "status", required = false) HorseStatus status,
            @RequestParam(name = "paidOnly", defaultValue = "false") boolean paidOnly,
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        boolean trainer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HEAD_TRAINER"));
        boolean manager = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLUB_MANAGER"));
        if (manager) return horseService.search(query, status, paidOnly, pageable);
        if (trainer) return horseService.searchForTrainer(query, status, userId(jwt), pageable);
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VETERINARIAN")))
            return horseService.searchForVeterinarian(query, status, pageable);
        return horseService.search(query, status, true, pageable);
    }

    @GetMapping("/eligible-for-medical")
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasAnyRole('CLUB_MANAGER','VETERINARIAN')")
    java.util.List<HorseResponse> getEligibleForMedical() {
        return horseService.getEligibleForMedical();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasRole('HORSE_OWNER')")
    Page<HorseResponse> mine(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return horseService.findOwnedBy(userId(jwt), pageable);
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','STABLE_STAFF','HORSE_OWNER')")
    HorseResponse getById(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return horseService.getByIdForUser(id, userId(jwt), accessRole(authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasRole('CLUB_MANAGER')")
    HorseResponse update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateHorseRequest request) {
        return horseService.update(id, request);
    }

    @PutMapping("/{id}/mine")
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasRole('HORSE_OWNER')")
    HorseResponse updateForOwner(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateOwnedHorseRequest request) {
        return horseService.updateForOwner(id, userId(jwt), request);
    }

    @PutMapping("/{id}/ownership")
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    HorseResponse replaceOwnership(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReplaceOwnershipRequest request) {
        return horseService.replaceOwnership(id, request);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    HorseResponse archive(@PathVariable("id") UUID id) {
        return horseService.archive(id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private Role accessRole(Authentication authentication) {
        for (Role role : new Role[] {Role.CLUB_MANAGER, Role.HEAD_TRAINER,
                Role.VETERINARIAN, Role.STABLE_STAFF, Role.HORSE_OWNER}) {
            if (authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()))) {
                return role;
            }
        }
        throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem hồ sơ ngựa");
    }
}
