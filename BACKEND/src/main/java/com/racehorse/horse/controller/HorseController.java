package com.racehorse.horse.controller;

import com.racehorse.horse.dto.CreateHorseRequest;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.dto.ReplaceOwnershipRequest;
import com.racehorse.horse.dto.UpdateHorseRequest;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER')")
    ResponseEntity<HorseResponse> create(@Valid @RequestBody CreateHorseRequest request) {
        HorseResponse response = horseService.create(request);
        return ResponseEntity
                .created(URI.create("/api/horses/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER')")
    HorseResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return horseService.getByIdForUser(id, userId(jwt), isPrivileged(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER')")
    Page<HorseResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) HorseStatus status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return horseService.search(query, status, pageable);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    Page<HorseResponse> mine(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return horseService.findOwnedBy(userId(jwt), pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER')")
    HorseResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHorseRequest request) {
        return horseService.update(id, request);
    }

    @PutMapping("/{id}/ownership")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER')")
    HorseResponse replaceOwnership(
            @PathVariable UUID id,
            @Valid @RequestBody ReplaceOwnershipRequest request) {
        return horseService.replaceOwnership(id, request);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER')")
    HorseResponse archive(@PathVariable UUID id) {
        return horseService.archive(id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean isPrivileged(Jwt jwt) {
        java.util.List<String> roles = jwt.getClaimAsStringList("roles");
        return roles.contains("CLUB_MANAGER") || roles.contains("HEAD_TRAINER");
    }
}
