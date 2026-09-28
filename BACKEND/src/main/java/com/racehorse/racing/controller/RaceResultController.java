package com.racehorse.racing.controller;

import com.racehorse.racing.dto.RaceHistoryResponse;
import com.racehorse.racing.dto.RaceResultResponse;
import com.racehorse.racing.dto.UpsertRaceResultRequest;
import com.racehorse.racing.service.RaceResultService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}/race-results")
public class RaceResultController {

    private final RaceResultService service;

    public RaceResultController(RaceResultService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_RACING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    RaceHistoryResponse history(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        boolean privileged = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_CLUB_MANAGER") || authority.getAuthority().equals("ROLE_HEAD_TRAINER"));
        return service.history(horseId, UUID.fromString(jwt.getSubject()), privileged);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_RACING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    ResponseEntity<RaceResultResponse> create(
            @PathVariable UUID horseId,
            @Valid @RequestBody UpsertRaceResultRequest request) {
        RaceResultResponse response = service.create(horseId, request);
        return ResponseEntity.created(URI.create(
                "/api/horses/" + horseId + "/race-results/" + response.id())).body(response);
    }

    @PutMapping("/{resultId}")
    @PreAuthorize("hasAuthority('PERM_RACING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    RaceResultResponse update(
            @PathVariable UUID horseId,
            @PathVariable UUID resultId,
            @Valid @RequestBody UpsertRaceResultRequest request) {
        return service.update(horseId, resultId, request);
    }

    @DeleteMapping("/{resultId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_RACING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public void delete(
            @PathVariable UUID horseId,
            @PathVariable UUID resultId) {
        service.delete(horseId, resultId);
    }
}
