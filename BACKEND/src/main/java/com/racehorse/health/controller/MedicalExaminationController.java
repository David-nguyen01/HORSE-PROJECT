package com.racehorse.health.controller;

import com.racehorse.health.dto.*;
import com.racehorse.health.service.MedicalExaminationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class MedicalExaminationController {

    private final MedicalExaminationService service;

    public MedicalExaminationController(MedicalExaminationService service) {
        this.service = service;
    }

    @PostMapping("/api/horses/{horseId}/medical-examinations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public MedicalExaminationResponse create(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMedicalExaminationRequest request) {
        return service.create(horseId, userId(jwt), request);
    }

    @PostMapping("/api/medical-examinations/{examId}/addenda")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public MedicalExaminationAddendumResponse addAddendum(
            @PathVariable UUID examId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddMedicalExaminationAddendumRequest request) {
        return service.addAddendum(examId, userId(jwt), request);
    }

    @GetMapping("/api/medical-examinations/{examId}")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')")
    public MedicalExaminationDetailResponse getDetail(
            @PathVariable UUID examId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {
        return service.getDetail(examId, userId(jwt), privileged(auth));
    }

    @GetMapping("/api/horses/{horseId}/medical-examinations")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')")
    public List<MedicalExaminationResponse> listByHorse(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {
        return service.listByHorse(horseId, userId(jwt), privileged(auth));
    }

    @DeleteMapping("/api/medical-examinations/{examId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('VETERINARIAN','CLUB_MANAGER') and (hasAuthority('PERM_MEDICAL_EDIT') or hasRole('CLUB_MANAGER'))")
    public void delete(
            @PathVariable UUID examId,
            @AuthenticationPrincipal Jwt jwt) {
        service.delete(examId, userId(jwt));
    }

    @DeleteMapping("/api/medical-examinations/addenda/{addendumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('VETERINARIAN','CLUB_MANAGER') and (hasAuthority('PERM_MEDICAL_EDIT') or hasRole('CLUB_MANAGER'))")
    public void deleteAddendum(
            @PathVariable UUID addendumId,
            @AuthenticationPrincipal Jwt jwt) {
        service.deleteAddendum(addendumId, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean privileged(Authentication a) {
        return a.getAuthorities().stream().anyMatch(
                x -> !x.getAuthority().equals("ROLE_HORSE_OWNER") && x.getAuthority().startsWith("ROLE_"));
    }
}
