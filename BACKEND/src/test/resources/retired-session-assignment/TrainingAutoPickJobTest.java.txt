package com.racehorse.training.job;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.caretask.service.CareTaskService;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.WorkloadIntensity;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingAutoPickJobTest {

    @Mock TrainingScheduleRepository scheduleRepository;
    @Mock UserAccountRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock CareTaskService careTaskService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T16:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private TrainingAutoPickJob job;

    @BeforeEach
    void setUp() {
        job = new TrainingAutoPickJob(
                scheduleRepository, userRepository, notificationService, careTaskService,
                "Asia/Ho_Chi_Minh", clock);
    }

    @Test
    void runAssignsBothStaffAndVetWhenAvailable() {
        LocalDate targetDate = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1);
        TrainingSchedule schedule = createUnassignedSchedule();

        when(scheduleRepository.findNeedingAssignmentForDate(targetDate))
                .thenReturn(List.of(schedule));

        UserAccount staff = user("staff_user", Role.STABLE_STAFF);
        UserAccount vet = user("vet_user", Role.VETERINARIAN);

        when(userRepository.findLeastAutoPickedStaff(targetDate)).thenReturn(Optional.of(staff));
        when(userRepository.findLeastAutoPickedVet(targetDate)).thenReturn(Optional.of(vet));

        job.run();

        assertThat(schedule.getAssignedStaff()).isEqualTo(staff);
        assertThat(schedule.getStaffAssignedBy()).isEqualTo("SYSTEM");
        assertThat(schedule.getStaffAssignedAt()).isEqualTo(clock.instant());

        assertThat(schedule.getVeterinarianUser()).isEqualTo(vet);
        assertThat(schedule.getVetAssignedBy()).isEqualTo("SYSTEM");
        assertThat(schedule.getVetAssignedAt()).isEqualTo(clock.instant());

        verify(scheduleRepository, times(2)).save(schedule);
        verify(careTaskService).createRecoveryTask(schedule, staff.getId());
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void runEscalatesToManagersWhenStaffOrVetUnavailable() {
        LocalDate targetDate = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1);
        TrainingSchedule schedule = createUnassignedSchedule();

        when(scheduleRepository.findNeedingAssignmentForDate(targetDate))
                .thenReturn(List.of(schedule));

        UserAccount manager = user("manager", Role.CLUB_MANAGER);
        when(userRepository.findLeastAutoPickedStaff(targetDate)).thenReturn(Optional.empty());
        when(userRepository.findLeastAutoPickedVet(targetDate)).thenReturn(Optional.empty());
        when(userRepository.findActiveClubManagersForUpdate()).thenReturn(List.of(manager));

        job.run();

        assertThat(schedule.getAssignedStaff()).isNull();
        assertThat(schedule.getVeterinarianUser()).isNull();

        verify(notificationService).create(
                eq(manager),
                eq("TRAINING_UNASSIGNED_STAFF"),
                eq(NotificationPriority.HIGH),
                any(), any(), eq("TRAINING_SCHEDULE"), eq(schedule.getId()));

        verify(notificationService).create(
                eq(manager),
                eq("TRAINING_UNASSIGNED_VET"),
                eq(NotificationPriority.HIGH),
                any(), any(), eq("TRAINING_SCHEDULE"), eq(schedule.getId()));

        verify(scheduleRepository, never()).save(schedule);
    }

    private TrainingSchedule createUnassignedSchedule() {
        UserAccount trainer = user("trainer", Role.HEAD_TRAINER);
        Horse horse = new Horse("H-SCHEDULE", "Schedule Test", null, LocalDate.of(2022, 1, 1),
                HorseGender.STALLION, "Bay", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
        TrainingPlan plan = new TrainingPlan(horse, "Active", null, trainer, true);
        TrainingPlanPhase phase = new TrainingPlanPhase(
                1, "Phase", LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 20),
                new BigDecimal("1500"), WorkloadIntensity.LOW, SurfaceType.GRASS);
        plan.replacePhases(List.of(phase));

        return new TrainingSchedule(
                phase, horse, null, "Trial Run", WorkloadIntensity.LOW,
                Instant.parse("2026-09-16T03:00:00Z"),
                Instant.parse("2026-09-16T05:00:00Z"),
                trainer);
    }

    private UserAccount user(String name, Role role) {
        return new UserAccount(name, name + "@test.local", "encoded", Set.of(role));
    }
}
