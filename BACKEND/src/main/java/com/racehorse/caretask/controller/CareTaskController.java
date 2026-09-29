package com.racehorse.caretask.controller;

import com.racehorse.caretask.dto.CareTaskResponse;
import com.racehorse.caretask.dto.CompleteCareTaskRequest;
import com.racehorse.caretask.dto.CreateCareTaskRequest;
import com.racehorse.caretask.service.CareTaskService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/care-tasks")
public class CareTaskController {

    private final CareTaskService service;

    public CareTaskController(CareTaskService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public List<CareTaskResponse> listTasks(
            @RequestParam(required = false) UUID horseId,
            @RequestParam(required = false) UUID assignedStaffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean overdueOnly) {
        return service.listTasks(horseId, assignedStaffId, date, status, overdueOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public CareTaskResponse getTask(@PathVariable UUID id) {
        return service.getTask(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public CareTaskResponse createTask(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCareTaskRequest request) {
        return service.createTask(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('STABLE_STAFF','HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public CareTaskResponse completeTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) CompleteCareTaskRequest request) {
        return service.completeTask(id, UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public CareTaskResponse cancelTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.cancelTask(id, UUID.fromString(jwt.getSubject()));
    }
}
