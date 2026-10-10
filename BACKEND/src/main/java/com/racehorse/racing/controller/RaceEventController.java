package com.racehorse.racing.controller;

import com.racehorse.racing.service.RaceEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_RACING_EDIT')")
    RaceEventService.EventView complete(@PathVariable UUID id) { return service.complete(id); }

    @PostMapping("/{eventId}/registrations") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_RACING_EDIT')")
    RaceEventService.RegistrationView register(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt,
                                                org.springframework.security.core.Authentication authentication,
                                                @Valid @RequestBody RegistrationRequest request) {
        return service.register(eventId, request.horseId(), user(jwt), request.registeredWeightKg(), manager(authentication));
    }

    @GetMapping("/registrations") @PreAuthorize("hasAuthority('PERM_RACING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    List<RaceEventService.RegistrationView> registrations(@RequestParam(required=false) UUID eventId,
             @RequestParam(required=false) UUID horseId, @RequestParam(required=false) String status,
             @AuthenticationPrincipal Jwt jwt, org.springframework.security.core.Authentication authentication) {
        boolean owner = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HORSE_OWNER"));
        boolean trainer = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HEAD_TRAINER"));
        return service.registrations(eventId, horseId, status,
                owner ? user(jwt) : null, trainer && !manager(authentication) ? user(jwt) : null);
    }

    @PostMapping("/registrations/{id}/decision")
    @PreAuthorize("hasRole('HORSE_OWNER') and hasAuthority('PERM_RACING_APPROVE')")
    RaceEventService.RegistrationView decide(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                               @RequestBody DecisionRequest request) {
        return service.decide(id, user(jwt), request.approve(), request.reason());
    }

    @PostMapping("/registrations/{id}/confirm")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and (hasAuthority('PERM_RACING_EDIT') or hasAuthority('PERM_RACING_APPROVE'))")
    RaceEventService.RegistrationView confirm(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                               org.springframework.security.core.Authentication authentication) {
        return service.confirm(id, user(jwt), manager(authentication));
    }

    @PostMapping("/registrations/{id}/withdraw")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_RACING_EDIT')")
    RaceEventService.RegistrationView withdraw(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                                org.springframework.security.core.Authentication authentication) {
        return service.withdraw(id, user(jwt), manager(authentication));
    }

    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private boolean manager(org.springframework.security.core.Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CLUB_MANAGER"));
    }

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
    public record DecisionRequest(boolean approve, String reason) {}
}
