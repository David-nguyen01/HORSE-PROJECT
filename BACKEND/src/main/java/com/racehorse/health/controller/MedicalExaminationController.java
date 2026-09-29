package com.racehorse.health.controller;

import com.racehorse.health.dto.*;
import com.racehorse.health.service.MedicalExaminationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class MedicalExaminationController {

    private final MedicalExaminationService service;

    public MedicalExaminationController(MedicalExaminationService service) {
        this.service = service;
    }

    // FR09 – Tạo hồ sơ khám mới; chỉ Bác sĩ thú y
    @PostMapping("/api/horses/{horseId}/examinations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') and hasRole('VETERINARIAN')")
    MedicalExaminationResponse create(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMedicalExaminationRequest request) {
        return service.create(userId(jwt), horseId, request);
    }

    // FR09 – Xem lịch sử khám theo thứ tự thời gian (chỉ đọc)
    @GetMapping("/api/horses/{horseId}/examinations")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    Page<MedicalExaminationResponse> list(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listForHorse(horseId, userId(jwt), isPrivileged(authentication), pageable);
    }

    // FR09 – Xem chi tiết 1 hồ sơ khám kèm phụ lục
    @GetMapping("/api/examinations/{examinationId}")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    MedicalExaminationResponse get(
            @PathVariable UUID examinationId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.getById(examinationId, userId(jwt), isPrivileged(authentication));
    }

    // FR09 – Thêm phụ lục (không xóa/sửa bản ghi gốc); Bác sĩ thú y và Quản lý
    @PostMapping("/api/examinations/{examinationId}/addenda")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') and hasAnyRole('VETERINARIAN','CLUB_MANAGER')")
    MedicalExaminationResponse addAddendum(
            @PathVariable UUID examinationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddExaminationAddendumRequest request) {
        return service.addAddendum(userId(jwt), examinationId, request);
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    private boolean isPrivileged(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_CLUB_MANAGER")
                        || a.getAuthority().equals("ROLE_HEAD_TRAINER")
                        || a.getAuthority().equals("ROLE_VETERINARIAN"));
    }
}
