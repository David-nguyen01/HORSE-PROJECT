package com.racehorse.stable.controller;

import com.racehorse.stable.service.StableService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stables")
public class StableController {
    private final StableService service;
    public StableController(StableService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    List<StableService.StableView> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @GetMapping("/{stableId}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    StableService.StableView get(@PathVariable UUID stableId) { return service.get(stableId); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.StableView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StableRequest request) {
        return service.create(user(jwt), request.name(), request.location(), request.stallCodes());
    }

    @PutMapping("/{stableId}")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.StableView update(@PathVariable UUID stableId, @Valid @RequestBody UpdateStableRequest request) {
        return service.update(stableId, request.name(), request.location(), request.active());
    }

    @PostMapping("/{stableId}/stalls") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.StableView addStall(@PathVariable UUID stableId, @AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody StallRequest request) {
        return service.addStall(stableId, user(jwt), request.code());
    }

    @DeleteMapping("/{stableId}/stalls/{stallId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    void deactivateStall(@PathVariable UUID stableId, @PathVariable UUID stallId) { service.deactivateStall(stableId, stallId); }

    @PostMapping("/{stableId}/stalls/{stallId}/assign")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.AssignmentView assign(@PathVariable UUID stableId, @PathVariable UUID stallId,
                                         @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignmentRequest request) {
        return service.assign(stableId, stallId, request.horseId(), user(jwt));
    }

    @PostMapping("/assignments/{assignmentId}/end")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.AssignmentView unassign(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        return service.unassign(assignmentId, user(jwt));
    }

    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    public record StableRequest(@NotBlank @Size(max=150) String name, @Size(max=300) String location,
                                List<@NotBlank @Size(max=50) String> stallCodes) {}
    public record UpdateStableRequest(@NotBlank @Size(max=150) String name, @Size(max=300) String location,
                                      Boolean active) {}
    public record StallRequest(@NotBlank @Size(max=50) String code) {}
    public record AssignmentRequest(@NotNull UUID horseId) {}
}
