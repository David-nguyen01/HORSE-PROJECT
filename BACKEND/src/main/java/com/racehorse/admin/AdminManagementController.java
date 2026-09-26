package com.racehorse.admin;

import com.racehorse.auth.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('CLUB_MANAGER')")
public class AdminManagementController {
    private final AdminManagementService service;
    public AdminManagementController(AdminManagementService service){this.service=service;}
    @GetMapping("/accounts")
    public List<Map<String,Object>> accounts(@RequestParam(required=false) String query,@RequestParam(required=false) Role role,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.accounts(query,role,enabled,page,size);}
    @PutMapping("/accounts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AccountEdit r){service.updateAccount(UUID.fromString(jwt.getSubject()),id,r.username(),r.email());}
    @PatchMapping("/accounts/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AccountStatus r){service.setEnabled(UUID.fromString(jwt.getSubject()),id,r.enabled());}
    @PostMapping("/accounts/{id}/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PasswordReset r){service.resetPassword(UUID.fromString(jwt.getSubject()),id,r.password());}
    @GetMapping("/horses/archived") @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_PROFILE_VIEW')")
    public List<Map<String,Object>> archived(@RequestParam(required=false) String query,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.archivedHorses(query,page,size);}
    @PatchMapping("/horses/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_PROFILE_EDIT')")
    public void restore(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){service.restore(UUID.fromString(jwt.getSubject()),id);}
    @PatchMapping("/horses/{id}/service-access") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void serviceAccess(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@RequestBody HorseServiceAccessRequest r){
        service.updateServiceAccess(UUID.fromString(jwt.getSubject()),id,r.paymentStatus(),r.serviceOverride(),r.overrideReason(),r.paidUntil());
    }
    public record AccountEdit(@NotBlank @Size(min=3,max=80) String username,@NotBlank @Email @Size(max=254) String email){}
    public record AccountStatus(@NotNull Boolean enabled){}
    public record PasswordReset(@NotBlank @Size(min=8,max=72) String password){}
    public record HorseServiceAccessRequest(String paymentStatus, Boolean serviceOverride, String overrideReason, java.time.LocalDate paidUntil){}
}
