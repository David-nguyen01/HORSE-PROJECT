package com.racehorse.nutrition.controller;

import com.racehorse.horse.service.OwnerHorseReadGuard;
import com.racehorse.stable.service.StaffHorseGuard;
import com.racehorse.nutrition.dto.ConfirmMealFeedingRequest;
import com.racehorse.nutrition.dto.DailyFeedingLogResponse;
import com.racehorse.nutrition.service.DailyFeedingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}/daily-feeding")
public class DailyFeedingController {

    private final DailyFeedingService dailyFeedingService;
    private final OwnerHorseReadGuard ownerGuard;
    private final StaffHorseGuard staffHorseGuard;

    public DailyFeedingController(DailyFeedingService dailyFeedingService, OwnerHorseReadGuard ownerGuard, StaffHorseGuard staffHorseGuard) {
        this.dailyFeedingService = dailyFeedingService;
        this.ownerGuard = ownerGuard;
        this.staffHorseGuard = staffHorseGuard;
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public DailyFeedingLogResponse confirmMeal(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody ConfirmMealFeedingRequest request) {
        UUID actorUserId = getActorId(jwt, authentication);
        staffHorseGuard.requireCaretaker(horseId, actorUserId, authentication);
        return dailyFeedingService.confirmMeal(horseId, actorUserId, request);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('STABLE_STAFF', 'VETERINARIAN') and hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')")
    public List<DailyFeedingLogResponse> getHistory(
            @PathVariable UUID horseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        ownerGuard.requireHorse(horseId, jwt, authentication);
        staffHorseGuard.requireCaretaker(horseId, getActorId(jwt, authentication), authentication);
        return dailyFeedingService.getHistory(horseId, startDate, endDate);
    }

    private UUID getActorId(Jwt jwt, Authentication authentication) {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {}
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {}
        }
        return UUID.randomUUID();
    }
}
