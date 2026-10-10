package com.racehorse.medical.controller;

import com.racehorse.medical.service.MedicalQueueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý hàng chờ tiếp nhận y tế.
 * <p>
 * Luồng: thanh toán hoặc bảo lãnh → nhân viên đưa vào phòng chờ → bác sĩ tiếp nhận,
 * lưu hồ sơ khám rồi hoàn tất. intakeStatus thay đổi độc lập với paymentStatus.
 */
@RestController
@RequestMapping("/api/medical/queue")
@PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_VIEW')")
public class MedicalQueueController {

    private final MedicalQueueService queueService;

    public MedicalQueueController(MedicalQueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * Danh sách ngựa đã ở phòng chờ và đang chờ bác sĩ tiếp nhận.
     */
    @GetMapping
    public List<MedicalQueueService.QueueItem> queue() {
        return queueService.getPendingQueue();
    }

    @GetMapping("/overview")
    public List<MedicalQueueService.QueueStatusItem> overview() {
        return queueService.getQueueOverview();
    }

    /**
     * Bác sĩ tiếp nhận ngựa vào khám y tế.
     * Ghi MEDICAL_ASSIGNMENT và đổi intakeStatus thành IN_MEDICAL.
     */
    @PostMapping("/{horseId}/receive")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public MedicalQueueService.AssignmentView receive(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt) {
        return queueService.receiveHorse(horseId, UUID.fromString(jwt.getSubject()));
    }

    /**
     * Sau khi đã lưu hồ sơ khám, chuyển ngựa sang chờ nhân viên phân ô chuồng.
     */
    @PostMapping("/{horseId}/complete")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public MedicalQueueService.AssignmentView completeMedical(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt) {
        return queueService.completeMedical(horseId, UUID.fromString(jwt.getSubject()));
    }

    /**
     * Danh sách ngựa đang được bác sĩ này khám (IN_MEDICAL).
     */
    @GetMapping("/my-assignments")
    public List<MedicalQueueService.AssignmentView> myAssignments(@AuthenticationPrincipal Jwt jwt) {
        return queueService.getMyAssignments(UUID.fromString(jwt.getSubject()));
    }
}
