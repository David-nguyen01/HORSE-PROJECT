package com.racehorse.racing.controller;

import com.racehorse.racing.service.RaceEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/races")
public class RaceEventController {
    private final RaceEventService service;
    public RaceEventController(RaceEventService service) { this.service = service; }

    @GetMapping @PreAuthorize("hasAuthority('PERM_RACING_VIEW')")
    List<RaceEventService.EventView> events(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Boolean active) { return service.events(from,to,active); }

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('PERM_RACING_VIEW')")
    RaceEventService.EventView event(@PathVariable UUID id) { return service.event(id); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_RACING_EDIT')")
    RaceEventService.EventView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EventRequest request) {
        return service.create(user(jwt), request.command(true));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_RACING_EDIT')")
    RaceEventService.EventView update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        return service.update(id, request.command(request.active() == null || request.active()));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_RACING_EDIT')")
    void deactivate(@PathVariable UUID id) { service.deactivate(id); }

    @PostMapping("/{eventId}/registrations") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HEAD_TRAINER') and hasAuthority('PERM_RACING_EDIT')")
    RaceEventService.RegistrationView register(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody RegistrationRequest request, Authentication auth) {
        return service.register(eventId, request.horseId(), user(jwt), request.registeredWeightKg(),
                hasRole(auth,"CLUB_MANAGER"),hasRole(auth,"HEAD_TRAINER"));
    }

    @GetMapping("/registrations") @PreAuthorize("hasAuthority('PERM_RACING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    List<RaceEventService.RegistrationView> registrations(@RequestParam(required=false) UUID eventId,
             @RequestParam(required=false) UUID horseId, @RequestParam(required=false) String status,
             @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return service.scopedRegistrations(eventId, horseId, status, user(jwt), hasRole(auth,"CLUB_MANAGER"),
                hasRole(auth,"HEAD_TRAINER"),hasRole(auth,"HORSE_OWNER"));
    }

    @GetMapping("/eligibility")
    @PreAuthorize("hasAuthority('PERM_RACING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    com.racehorse.health.dto.RaceReadinessResponse eligibility(@RequestParam UUID horseId,
            @RequestParam(required=false) UUID eventId, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return service.eligibility(horseId,eventId,user(jwt),hasRole(auth,"CLUB_MANAGER"),
                hasRole(auth,"HEAD_TRAINER"),hasRole(auth,"HORSE_OWNER"));
    }

    @PostMapping("/registrations/{id}/decision")
    @PreAuthorize("hasAuthority('PERM_RACING_APPROVE') and hasAnyRole('CLUB_MANAGER','HORSE_OWNER')")
    RaceEventService.RegistrationView decide(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DecisionRequest request, Authentication auth) {
        return service.decide(id,user(jwt),request.approve(),request.reason(),hasRole(auth,"CLUB_MANAGER"));
    }

    @PostMapping("/registrations/{id}/withdraw")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_RACING_EDIT')")
    RaceEventService.RegistrationView withdraw(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WithdrawalRequest request, Authentication auth) {
        return service.withdraw(id, user(jwt),request.reason(),hasRole(auth,"CLUB_MANAGER"));
    }

    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private boolean hasRole(Authentication authentication,String role){return authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));}

    public record EventRequest(@NotBlank @Size(max=200) String raceName, @NotBlank @Size(max=200) String racecourse,
            @NotNull LocalDate raceDate, @NotNull OffsetDateTime registrationDeadline,
            @NotNull @DecimalMin("0.01") BigDecimal distanceMeters, @NotBlank @Size(max=100) String raceClass,
            @Min(0) Integer minimumAgeYears, @Min(0) Integer maximumAgeYears,
            @DecimalMin("0.01") BigDecimal minimumWeightKg, @DecimalMin("0.01") BigDecimal maximumWeightKg,
            Boolean active) {
        RaceEventService.EventCommand command(boolean activeValue) { return new RaceEventService.EventCommand(raceName,
                racecourse,raceDate,registrationDeadline,distanceMeters,raceClass,minimumAgeYears,maximumAgeYears,
                minimumWeightKg,maximumWeightKg,activeValue); }
    }
    public record RegistrationRequest(@NotNull UUID horseId, @DecimalMin("0.01") BigDecimal registeredWeightKg) {}
    public record DecisionRequest(@NotNull Boolean approve, String reason) {}
    public record WithdrawalRequest(@NotBlank @Size(max=1000) String reason) {}
}
