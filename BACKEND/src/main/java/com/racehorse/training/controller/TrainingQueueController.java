package com.racehorse.training.controller;

import com.racehorse.training.service.TrainingQueueService;
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
 * Controller quản lý hàng chờ tiếp nhận huấn luyện.
 * <p>
 * Luồng: Sau khi bác sĩ hoàn tất khám → ngựa có paymentStatus=PENDING_TRAINING →
 * Trainer gọi receive → ghi TRAINER_ASSIGNMENT → cập nhật paymentStatus=IN_TRAINING.
 * Sau đó trainer hoàn tất → paymentStatus=COMPLETED (ngựa chính thức hoạt động).
 */
@RestController
@RequestMapping("/api/training/queue")
@PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_TRAINING_VIEW')")
public class TrainingQueueController {

    private final TrainingQueueService queueService;

    public TrainingQueueController(TrainingQueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * Danh sách ngựa đang chờ tiếp nhận huấn luyện (paymentStatus = PENDING_TRAINING).
     */
    @GetMapping
    List<TrainingQueueService.QueueItem> queue() {
        return queueService.getPendingQueue();
    }

    /**
     * Trainer tiếp nhận ngựa vào huấn luyện.
     */
    @PostMapping("/{horseId}/receive")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_TRAINING_EDIT')")
    TrainingQueueService.AssignmentView receive(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt) {
        return queueService.receiveHorse(horseId, UUID.fromString(jwt.getSubject()));
    }

    /**
     * Trainer hoàn tất đánh giá ban đầu → ngựa chính thức hoạt động.
     */
    @PostMapping("/{horseId}/complete")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_TRAINING_EDIT')")
    TrainingQueueService.AssignmentView complete(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt) {
        return queueService.completeOnboarding(horseId, UUID.fromString(jwt.getSubject()));
    }

    /**
     * Danh sách ngựa đang được trainer này huấn luyện (IN_TRAINING).
     */
    @GetMapping("/my-assignments")
    List<TrainingQueueService.AssignmentView> myAssignments(@AuthenticationPrincipal Jwt jwt) {
        return queueService.getMyAssignments(UUID.fromString(jwt.getSubject()));
    }
}
