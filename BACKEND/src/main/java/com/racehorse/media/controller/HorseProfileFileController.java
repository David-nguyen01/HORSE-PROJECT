package com.racehorse.media.controller;

import com.racehorse.media.dto.HorseProfileFileResponse;
import com.racehorse.media.service.HorseProfileFileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}/main-file")
public class HorseProfileFileController {

    private final HorseProfileFileService service;

    public HorseProfileFileController(HorseProfileFileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW')")
    HorseProfileFileResponse get(@PathVariable UUID horseId) {
        return service.getActive(horseId);
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    HorseProfileFileResponse replace(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) {
        return service.replaceDirectly(horseId, userId(jwt), file);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    ResponseEntity<Void> delete(@PathVariable UUID horseId) {
        service.deleteActive(horseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/proposals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasRole('HORSE_OWNER')")
    ResponseEntity<HorseProfileFileResponse> propose(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) {
        HorseProfileFileResponse response = service.propose(horseId, userId(jwt), file);
        return ResponseEntity.created(URI.create(
                "/api/horses/" + horseId + "/main-file/proposals/" + response.id())).body(response);
    }

    @GetMapping("/proposals")
    @PreAuthorize("hasAuthority('PERM_PROFILE_APPROVE') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    List<HorseProfileFileResponse> pendingProposals(@PathVariable UUID horseId) {
        return service.pendingProposals(horseId);
    }

    @PostMapping("/proposals/{proposalId}/approve")
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    HorseProfileFileResponse approve(
            @PathVariable UUID horseId,
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.approve(horseId, proposalId, userId(jwt));
    }

    @PostMapping("/proposals/{proposalId}/reject")
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    ResponseEntity<Void> reject(
            @PathVariable UUID horseId,
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal Jwt jwt) {
        service.reject(horseId, proposalId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
