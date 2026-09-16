package com.racehorse.health.controller;

import com.racehorse.health.dto.*;
import com.racehorse.health.service.PreventiveCareService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class PreventiveCareController {
    private final PreventiveCareService service;
    public PreventiveCareController(PreventiveCareService service){this.service=service;}
    @PostMapping("/api/horses/{horseId}/preventive-care") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    PreventiveCareResponse create(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreatePreventiveCareRequest request){
        return service.create(horseId,UUID.fromString(jwt.getSubject()),request);
    }
    @PostMapping("/api/preventive-care/{id}/complete")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    PreventiveCareResponse complete(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompletePreventiveCareRequest request){
        return service.complete(id,UUID.fromString(jwt.getSubject()),request);
    }
}
