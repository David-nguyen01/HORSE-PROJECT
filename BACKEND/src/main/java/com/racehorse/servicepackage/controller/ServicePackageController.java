package com.racehorse.servicepackage.controller;

import com.racehorse.servicepackage.dto.*;
import com.racehorse.servicepackage.service.ServicePackageService;
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
@RequestMapping("/api/service-packages")
public class ServicePackageController {

    private final ServicePackageService service;

    public ServicePackageController(ServicePackageService service) {
        this.service = service;
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HORSE_OWNER')")
    public List<ServicePackageResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HORSE_OWNER')")
    public List<ServicePackageResponse> catalog() {
        return service.listActive();
    }

    @GetMapping("/reserve")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public List<ServicePackageResponse> listReserve() {
        return service.listReserve();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HORSE_OWNER')")
    public ServicePackageResponse get(
            @PathVariable UUID id,
            Authentication authentication) {
        ServicePackageResponse pkg = service.get(id);
        if (!pkg.isActive() && !isClubManager(authentication)) {
            throw new AccessDeniedException("Chỉ Quản lý CLB (Club Manager) mới có quyền truy cập gói dịch vụ trong Thư viện dự phòng");
        }
        return pkg;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ServicePackageResponse create(
            @Valid @RequestBody CreateServicePackageRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.create(actorId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ServicePackageResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServicePackageRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.update(id, actorId, request);
    }

    @PostMapping("/{id}/move-to-active")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ServicePackageResponse moveToActive(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.transfer(id, actorId, true);
    }

    @PostMapping("/{id}/move-to-reserve")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ServicePackageResponse moveToReserve(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.transfer(id, actorId, false);
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ServicePackageResponse transfer(
            @PathVariable UUID id,
            @Valid @RequestBody TransferServicePackageRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.transfer(id, actorId, request.active());
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public List<ServicePackageResponse> reorder(
            @Valid @RequestBody ReorderServicePackagesRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        return service.reorder(actorId, request.orderedIds());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actorId = getActorId(jwt, authentication);
        service.delete(id, actorId);
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
