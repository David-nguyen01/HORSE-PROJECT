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
 * Luồng: Sau khi Owner thanh toán → ngựa có paymentStatus=PAID →
 * Bác sĩ gọi receive → ghi MEDICAL_ASSIGNMENT → cập nhật paymentStatus=IN_MEDICAL.
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
     * Danh sách ngựa đang chờ tiếp nhận y tế (paymentStatus = PAID, chưa có MEDICAL_ASSIGNMENT active).
     */
    @GetMapping
    public List<MedicalQueueService.QueueItem> queue() {
        return queueService.getPendingQueue();
    }

    /**
     * Bác sĩ tiếp nhận ngựa vào khám y tế.
     * Ghi module_records (MEDICAL_ASSIGNMENT), đổi paymentStatus = IN_MEDICAL.
     */
    @PostMapping("/{horseId}/receive")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public MedicalQueueService.AssignmentView receive(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt) {
        return queueService.receiveHorse(horseId, UUID.fromString(jwt.getSubject()));
    }

    /**
     * Bác sĩ hoàn tất khám → chuyển ngựa sang chờ huấn luyện.
     * Đổi paymentStatus = PENDING_TRAINING.
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
