package com.racehorse.training.job;

import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.caretask.service.CareTaskService;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class TrainingAutoPickJob {

    private static final Logger log = LoggerFactory.getLogger(TrainingAutoPickJob.class);

    private final TrainingScheduleRepository scheduleRepository;
    private final UserAccountRepository userRepository;
    private final NotificationService notificationService;
    private final CareTaskService careTaskService;
    private final ZoneId zone;
    private final Clock clock;

    public TrainingAutoPickJob(
            TrainingScheduleRepository scheduleRepository,
            UserAccountRepository userRepository,
            NotificationService notificationService,
            CareTaskService careTaskService,
            @Value("${app.club-time-zone:Asia/Ho_Chi_Minh}") String zone,
            Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.careTaskService = careTaskService;
        this.zone = ZoneId.of(zone);
        this.clock = clock;
    }

    /**
     * Chạy lúc 23:00 ICT mỗi ngày.
     * Gán staff/vet cho các phiên tập ngày hôm sau chưa có người nhận.
     */
    @Scheduled(cron = "0 0 23 * * *", zone = "${app.club-time-zone:Asia/Ho_Chi_Minh}")
    @Transactional
    public void run() {
        LocalDate targetDate = clock.instant().atZone(zone).toLocalDate().plusDays(1);
        List<TrainingSchedule> openSessions =
                scheduleRepository.findNeedingAssignmentForDate(targetDate);

        log.info("TrainingAutoPickJob running for {}: {} session(s) needing assignment", targetDate, openSessions.size());

        for (TrainingSchedule s : openSessions) {
            if (s.getAssignedStaff() == null) {
                pickStaff(s, targetDate);
            }
            if (s.getVeterinarianUser() == null) {
                pickVet(s, targetDate);
            }
        }
    }

    private void pickVet(TrainingSchedule s, LocalDate targetDate) {
        userRepository.findLeastAutoPickedVet(targetDate).ifPresentOrElse(
            vet -> {
                s.setVeterinarianUser(vet);
                s.setVetAssignedBy("SYSTEM");
                s.setVetAssignedAt(clock.instant());
                scheduleRepository.save(s);
                log.info("Auto-assigned Vet {} to training session {}", vet.getUsername(), s.getId());
            },
            () -> {
                log.warn("No available Vet for session {} on {}", s.getId(), targetDate);
                userRepository.findActiveClubManagersForUpdate().forEach(manager -> {
                    notificationService.create(
                            manager,
                            "TRAINING_UNASSIGNED_VET",
                            NotificationPriority.HIGH,
                            "Thiếu Bác sĩ thú y cho buổi tập",
                            "Không còn bác sĩ thú y trống lịch cho buổi tập ngày "
                                    + targetDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                    + ". Yêu cầu Quản lý điều phối khẩn cấp.",
                            "TRAINING_SCHEDULE",
                            s.getId()
                    );
                });
            }
        );
    }

    private void pickStaff(TrainingSchedule s, LocalDate targetDate) {
        userRepository.findLeastAutoPickedStaff(targetDate).ifPresentOrElse(
            staff -> {
                s.setAssignedStaff(staff);
                s.setStaffAssignedBy("SYSTEM");
                s.setStaffAssignedAt(clock.instant());
                scheduleRepository.save(s);
                if (careTaskService != null) {
                    careTaskService.createRecoveryTask(s, staff.getId());
                }
                log.info("Auto-assigned Staff {} to training session {}", staff.getUsername(), s.getId());
            },
            () -> {
                log.warn("No available Staff for session {} on {}", s.getId(), targetDate);
                userRepository.findActiveClubManagersForUpdate().forEach(manager -> {
                    notificationService.create(
                            manager,
                            "TRAINING_UNASSIGNED_STAFF",
                            NotificationPriority.HIGH,
                            "Thiếu Nhân viên chuồng cho buổi tập",
                            "Không còn nhân viên chuồng trống lịch cho buổi tập ngày "
                                    + targetDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                    + ". Yêu cầu Quản lý điều phối khẩn cấp.",
                            "TRAINING_SCHEDULE",
                            s.getId()
                    );
                });
            }
        );
    }
}
