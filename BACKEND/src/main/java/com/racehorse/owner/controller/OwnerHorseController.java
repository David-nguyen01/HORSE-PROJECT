package com.racehorse.owner.controller;

import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.owner.dto.OwnerCreateHorseRequest;
import com.racehorse.owner.dto.OwnerUpdateHorseRequest;
import com.racehorse.owner.service.OwnerHorseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Controller dành riêng cho HORSE_OWNER đăng ký và quản lý ngựa của mình.
 * <p>
 * Các endpoint:
 * - POST   /api/owner/horses          → Tạo hồ sơ ngựa mới (DRAFT)
 * - PUT    /api/owner/horses/{id}     → Sửa hồ sơ ngựa (chỉ khi DRAFT)
 * - POST   /api/owner/horses/{id}/submit → Gửi hồ sơ → chờ thanh toán
 * - GET    /api/owner/horses/{id}/pedigree → Xem thông tin phả hệ thủ công
 */
@RestController
@RequestMapping("/api/owner/horses")
@PreAuthorize("hasRole('HORSE_OWNER')")
public class OwnerHorseController {

    private final OwnerHorseService ownerHorseService;

    public OwnerHorseController(OwnerHorseService ownerHorseService) {
        this.ownerHorseService = ownerHorseService;
    }

    /**
     * Owner tạo hồ sơ ngựa mới (trạng thái DRAFT, paymentStatus = UNPAID).
     */
    @PostMapping
    ResponseEntity<HorseResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OwnerCreateHorseRequest request) {
        HorseResponse response = ownerHorseService.createDraft(userId(jwt), request);
        return ResponseEntity
                .created(URI.create("/api/owner/horses/" + response.id()))
                .body(response);
    }

    /**
     * Owner cập nhật hồ sơ ngựa (chỉ khi paymentStatus = UNPAID → DRAFT).
     */
    @PutMapping("/{id}")
    HorseResponse update(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OwnerUpdateHorseRequest request) {
        return ownerHorseService.updateDraft(userId(jwt), id, request);
    }

    /**
     * Owner gửi hồ sơ ngựa → chuyển sang chờ thanh toán (PENDING_PAYMENT).
     */
    @PostMapping("/{id}/submit")
    HorseResponse submit(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ownerHorseService.submit(userId(jwt), id);
    }

    /**
     * Xem thông tin phả hệ thủ công (nếu có) của ngựa.
     */
    @GetMapping("/{id}/pedigree")
    Map<String, String> pedigree(@PathVariable UUID id) {
        return ownerHorseService.getManualPedigree(id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
