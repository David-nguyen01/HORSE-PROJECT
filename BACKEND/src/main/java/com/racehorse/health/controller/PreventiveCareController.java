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
import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;

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
    @GetMapping("/api/horses/{horseId}/preventive-care")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW')")
    List<PreventiveCareService.CareView> list(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,
            @RequestParam(required=false) String careType,@RequestParam(required=false) String status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        return service.list(horseId,UUID.fromString(jwt.getSubject()),privileged(auth),careType,status,from,to);
    }
    @PutMapping("/api/preventive-care/{id}")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    PreventiveCareService.CareView update(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreatePreventiveCareRequest request){
        return service.update(id,request,UUID.fromString(jwt.getSubject()));
    }
    @DeleteMapping("/api/preventive-care/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    void cancel(@PathVariable UUID id){service.cancel(id);}
    private boolean privileged(Authentication a){return a.getAuthorities().stream().anyMatch(x->!x.getAuthority().equals("ROLE_HORSE_OWNER")&&x.getAuthority().startsWith("ROLE_"));}
}
