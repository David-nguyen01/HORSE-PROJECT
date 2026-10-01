package com.racehorse.horse.controller;

import com.racehorse.horse.service.HorseAssignmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller trả về thông tin phân công "4 người" của ngựa:
 * Owner, Bác sĩ (Veterinarian), Huấn luyện viên (Trainer), Người chăm sóc (CareStaff).
 * <p>
 * Endpoint riêng biệt, KHÔNG sửa HorseResponse.
 */
@RestController
@RequestMapping("/api/horses")
public class HorseAssignmentController {

    private final HorseAssignmentService assignmentService;

    public HorseAssignmentController(HorseAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Lấy thông tin phân công 4 người cho ngựa.
     */
    @GetMapping("/{id}/assignments")
    @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW')")
    HorseAssignmentService.HorseAssignmentsResponse assignments(@PathVariable UUID id) {
        return assignmentService.getAssignments(id);
    }
}
