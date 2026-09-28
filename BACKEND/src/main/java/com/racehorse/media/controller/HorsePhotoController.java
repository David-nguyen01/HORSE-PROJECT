package com.racehorse.media.controller;

import com.racehorse.media.dto.HorsePhotoResponse;
import com.racehorse.media.service.HorsePhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}/photos")
public class HorsePhotoController {

    private final HorsePhotoService service;

    public HorsePhotoController(HorsePhotoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW')")
    public List<HorsePhotoResponse> list(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {
        return service.list(horseId, userId(jwt), isPrivileged(auth));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public HorsePhotoResponse upload(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder,
            @RequestParam(value = "caption", required = false) String caption) {
        return service.upload(horseId, userId(jwt), file, displayOrder, caption);
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public void delete(
            @PathVariable UUID horseId,
            @PathVariable UUID photoId,
            @AuthenticationPrincipal Jwt jwt) {
        service.delete(horseId, photoId, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_CLUB_MANAGER")
                || authority.getAuthority().equals("ROLE_HEAD_TRAINER")
                || authority.getAuthority().equals("ROLE_VETERINARIAN")
                || authority.getAuthority().equals("ROLE_STABLE_STAFF"));
    }
}
