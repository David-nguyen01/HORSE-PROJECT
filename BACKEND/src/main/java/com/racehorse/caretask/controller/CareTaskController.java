package com.racehorse.caretask.controller;

import com.racehorse.caretask.dto.CareTaskResponse;
import com.racehorse.caretask.dto.CompleteCareTaskRequest;
import com.racehorse.caretask.dto.CreateCareTaskRequest;
import com.racehorse.caretask.service.CareTaskService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
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

    @GetMapping("/{id}/photo")
    public ResponseEntity<?> getTaskPhoto(@PathVariable UUID id) {
        CareTaskService.TaskPhotoData photo = service.getTaskPhoto(id);
        if (photo.bytes() != null && photo.bytes().length > 0) {
            String ct = photo.contentType() != null && !photo.contentType().isBlank()
                    ? photo.contentType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                    .contentType(MediaType.parseMediaType(ct))
                    .body(photo.bytes());
        }
        if (photo.photoUrl() != null && photo.photoUrl().startsWith("http")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(photo.photoUrl())).build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasAnyRole('HEAD_TRAINER','CLUB_MANAGER')")
    public CareTaskResponse createTask(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCareTaskRequest request) {
        return service.createTask(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping(value = "/{id}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasAnyRole('STABLE_STAFF','HEAD_TRAINER','CLUB_MANAGER')")
    public CareTaskResponse completeTaskMultipart(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String notes,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.completeTask(id, UUID.fromString(jwt.getSubject()), notes, file);
    }

    @PostMapping(value = "/{id}/complete", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasAnyRole('STABLE_STAFF','HEAD_TRAINER','CLUB_MANAGER')")
    public CareTaskResponse completeTaskJson(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) CompleteCareTaskRequest request) {
        return service.completeTask(id, UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasAnyRole('HEAD_TRAINER','CLUB_MANAGER')")
    public CareTaskResponse cancelTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.cancelTask(id, UUID.fromString(jwt.getSubject()));
    }
}
