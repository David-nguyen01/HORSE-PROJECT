package com.racehorse.stable.controller;

import com.racehorse.stable.service.StableService;
import com.racehorse.horse.service.OwnerHorseReadGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stables")
public class StableController {
    private final StableService service;
    private final OwnerHorseReadGuard ownerGuard;
    public StableController(StableService service, OwnerHorseReadGuard ownerGuard) {
        this.service = service;
        this.ownerGuard = ownerGuard;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')")
    List<StableService.StableView> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @GetMapping("/{stableId}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')")
    StableService.StableView get(@PathVariable UUID stableId) { return service.get(stableId); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    StableService.StableView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StableRequest request) {
        return service.create(user(jwt), request.name(), request.location(), request.stallCodes());
    }

    @PutMapping("/{stableId}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    StableService.StableView update(@PathVariable UUID stableId, @Valid @RequestBody UpdateStableRequest request) {
        return service.update(stableId, request.name(), request.location(), request.active());
    }

    @DeleteMapping("/{stableId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    void deleteStable(@PathVariable UUID stableId, @AuthenticationPrincipal Jwt jwt) {
        service.delete(stableId, user(jwt));
    }

    @PostMapping("/{stableId}/stalls") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    StableService.StableView addStall(@PathVariable UUID stableId, @AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody StallRequest request) {
        return service.addStall(stableId, user(jwt), request.code());
    }

    @DeleteMapping("/{stableId}/stalls/{stallId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    void deleteStall(@PathVariable UUID stableId, @PathVariable UUID stallId, @AuthenticationPrincipal Jwt jwt) {
        service.deleteStall(stableId, stallId, user(jwt));
    }

    @PostMapping("/{stableId}/stalls/{stallId}/assign")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.AssignmentView assign(@PathVariable UUID stableId, @PathVariable UUID stallId,
                                         @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignmentRequest request) {
        return service.assign(stableId, stallId, request.horseId(), user(jwt));
    }

    @PostMapping("/horses/{horseId}/transfer")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.AssignmentView transfer(@PathVariable UUID horseId, @AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody TransferRequest request) {
        return service.transferHorse(horseId, request.targetStableId(), request.targetStallId(), user(jwt), request.reason());
    }

    @PostMapping("/assignments/{assignmentId}/end")
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.AssignmentView unassign(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        return service.unassign(assignmentId, user(jwt));
    }

    // Daily Routines (FR13)
    @GetMapping("/horses/{horseId}/daily-routines")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    List<StableService.DailyRoutineView> listDailyRoutines(@PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        ownerGuard.requireHorse(horseId, jwt, authentication);
        return service.listDailyRoutines(horseId);
    }

    @PostMapping("/horses/{horseId}/daily-routines") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.DailyRoutineView addDailyRoutine(@PathVariable UUID horseId, @AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody StableService.DailyRoutineRequest request) {
        return service.addDailyRoutine(horseId, user(jwt), request);
    }

    @PutMapping("/daily-routines/{routineId}")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.DailyRoutineView updateDailyRoutine(@PathVariable UUID routineId, @AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody StableService.DailyRoutineRequest request) {
        return service.updateDailyRoutine(routineId, user(jwt), request);
    }

    @DeleteMapping("/daily-routines/{routineId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    void deleteDailyRoutine(@PathVariable UUID routineId) {
        service.deleteDailyRoutine(routineId);
    }

    @GetMapping("/routines/backfill-preview")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    StableService.RoutineBackfillPreview previewRoutineBackfill() {
        return service.previewRoutineBackfill();
    }

    @PostMapping("/routines/backfill-execute")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.RoutineBackfillPreview executeRoutineBackfill(@AuthenticationPrincipal Jwt jwt) {
        return service.executeRoutineBackfill(user(jwt));
    }

    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    public record StableRequest(@NotBlank @Size(max=150) String name, @Size(max=300) String location,
                                List<@NotBlank @Size(max=50) String> stallCodes) {}
    public record UpdateStableRequest(@NotBlank @Size(max=150) String name, @Size(max=300) String location,
                                      Boolean active) {}
    public record StallRequest(@NotBlank @Size(max=50) String code) {}
    public record AssignmentRequest(@NotNull UUID horseId) {}
    public record TransferRequest(@NotNull UUID targetStableId, @NotNull UUID targetStallId, String reason) {}
}
