package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.entity.TrainingLock;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.training.dto.CreateTrainingScheduleRequest;
import com.racehorse.training.dto.UpdateTrainingScheduleRequest;
import com.racehorse.training.entity.Racetrack;
import com.racehorse.training.entity.RacetrackLane;
import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.TrialRunBooking;
import com.racehorse.training.entity.WorkloadIntensity;
import com.racehorse.training.repository.RacetrackLaneRepository;
import com.racehorse.training.repository.RacetrackRepository;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingPlanPhaseRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import com.racehorse.training.repository.TrialRunBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.eq;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingScheduleServiceTest {
    @Mock TrainingScheduleRepository repository;
    @Mock TrainingPlanPhaseRepository phaseRepository;
    @Mock RacetrackRepository trackRepository;
    @Mock RacetrackLaneRepository laneRepository;
    @Mock TrialRunBookingRepository bookingRepository;
    @Mock TrainingLockRepository lockRepository;
    @Mock UserAccountRepository userRepository;
    @Mock HorseAccessService horseAccessService;
    @Mock TrainingEvaluationRepository evaluationRepository;
    @Mock NotificationService notificationService;
    @Mock JdbcTemplate jdbc;

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");
    private static final LocalDate TRAINING_DATE = LocalDate.of(2026, 9, 15);
    private final UUID horseId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID phaseId = UUID.randomUUID();
    private final UUID trackId = UUID.randomUUID();
    private final UUID laneId = UUID.randomUUID();
    private TrainingScheduleService service;
    private Horse horse;
    private TrainingPlanPhase phase;
    private Racetrack track;
    private RacetrackLane lane;
    private UserAccount trainer;

    @BeforeEach
    void setUp() {
        service = new TrainingScheduleService(repository, phaseRepository, trackRepository, laneRepository,
                bookingRepository, lockRepository, userRepository, horseAccessService,
                Clock.fixed(NOW, ZoneOffset.UTC), evaluationRepository, notificationService, jdbc);
        org.mockito.Mockito.lenient().when(jdbc.queryForObject(
                org.mockito.ArgumentMatchers.contains("diagnosing_vet_user_id is not null"),
                org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenReturn(1);
        horse = new Horse("H-001", "Lightning", null, LocalDate.of(2022, 1, 1),
                HorseGender.STALLION, "Bay", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
        ReflectionTestUtils.setField(horse, "id", horseId);
        trainer = new UserAccount("trainer-a", "trainer-a@test.local", "encoded", Set.of(Role.HEAD_TRAINER));
        TrainingPlan plan = new TrainingPlan(horse, "Active plan", null, trainer, true);
        phase = new TrainingPlanPhase(1, "Phase", TRAINING_DATE.minusDays(1), TRAINING_DATE.plusDays(10),
                BigDecimal.valueOf(1500), WorkloadIntensity.MEDIUM, SurfaceType.GRASS);
        plan.replacePhases(List.of(phase));
        ReflectionTestUtils.setField(phase, "id", phaseId);
        track = new Racetrack("Track", null);
        ReflectionTestUtils.setField(track, "id", trackId);
        lane = new RacetrackLane(track, 1);
        ReflectionTestUtils.setField(lane, "id", laneId);
    }

    @Test
    void blocksHighIntensityWhenHorseHasActiveTrainingLock() {
        phase = new TrainingPlanPhase(1, "High", TRAINING_DATE, TRAINING_DATE.plusDays(10),
                BigDecimal.valueOf(1500), WorkloadIntensity.HIGH, SurfaceType.GRASS);
        TrainingPlan plan = new TrainingPlan(horse, "Active plan", null, trainer, true);
        plan.replacePhases(List.of(phase));
        ReflectionTestUtils.setField(phase, "id", phaseId);
        prepareCreate(false);
        when(lockRepository.findByHorseIdAndReleasedAtIsNull(horseId))
                .thenReturn(Optional.of(new TrainingLock(horse, "Injury", null, trainer, NOW)));

        assertThatThrownBy(() -> service.create(actorId, request(), false, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("khóa huấn luyện");
        verify(repository, never()).saveAndFlush(any(TrainingSchedule.class));
    }

    @Test
    void blocksOccupiedLaneForAnotherSessionOnTheSameDate() {
        prepareCreate(true);

        assertThatThrownBy(() -> service.create(actorId, request(), false, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Làn chạy");
    }

    @Test
    void blocksExpiredPlansBeforeCreatingSessions() {
        TrainingPlan expired = new TrainingPlan(horse, "Expired", null, trainer, true);
        TrainingPlanPhase oldPhase = new TrainingPlanPhase(1, "Old", TRAINING_DATE.minusDays(20),
                TRAINING_DATE.minusDays(1), BigDecimal.valueOf(1500), WorkloadIntensity.MEDIUM, SurfaceType.GRASS);
        expired.replacePhases(List.of(oldPhase));
        when(phaseRepository.findDetailById(phaseId)).thenReturn(Optional.of(oldPhase));
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);

        assertThatThrownBy(() -> service.create(actorId, request(), false, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hết hạn");
    }

    @Test
    void trainerAccessIsPassedToReadAndWriteScopeChecks() {
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(repository.findCalendar(any(), any(), any(), any())).thenReturn(List.of());

        service.calendar(TRAINING_DATE, TRAINING_DATE, horseId, actorId, false, true, false);

        verify(horseAccessService).requireTrainingAccess(horseId, actorId, false, true, false);
    }

    @Test
    void trainerCalendarWithoutHorseFilterIsScopedToCurrentTrainer() {
        when(repository.findCalendar(any(), any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(actorId)))
                .thenReturn(List.of());

        service.calendar(TRAINING_DATE, TRAINING_DATE.plusDays(6), null, actorId, false, true, false);

        verify(repository).findCalendar(any(), any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(actorId));
    }

    @Test
    void ownerScheduleListIncludesOnlyPublishedNonCancelledSessionsAndOmitsStaff() {
        horse.assignCurrentTrainer(trainer);
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, false, true)).thenReturn(horse);
        TrainingSchedule draft = schedule();
        TrainingSchedule published = schedule();
        published.publish(NOW.minusSeconds(1));
        published.complete(NOW);
        TrainingSchedule cancelled = schedule();
        cancelled.publish(NOW.minusSeconds(1));
        cancelled.cancel(NOW);
        ReflectionTestUtils.setField(published, "id", UUID.randomUUID());
        when(repository.findOwnerVisibleByHorseId(horseId)).thenReturn(List.of(draft, published, cancelled));
        when(bookingRepository.findByTrainingScheduleId(published.getId())).thenReturn(Optional.empty());
        when(evaluationRepository.findByTrainingScheduleId(published.getId())).thenReturn(Optional.empty());

        var result = service.ownerSchedules(horseId, actorId, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo("COMPLETED");
        assertThat(result.getFirst().evaluation()).isNull();
        assertThat(java.util.Arrays.stream(result.getFirst().getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("assignedStaff", "veterinarian", "staffAssignedBy");
        verify(horseAccessService).requireTrainingAccess(horseId, actorId, false, false, true);
    }

    @Test
    void ownerCannotReadScheduleForHorseTheyDoNotOwn() {
        UUID scheduleId = UUID.randomUUID();
        TrainingSchedule published = schedule();
        published.publish(NOW.minusSeconds(1));
        when(repository.findDetailById(scheduleId)).thenReturn(Optional.of(published));
        doThrow(new ResourceNotFoundException("Not owned"))
                .when(horseAccessService).requireTrainingAccess(horseId, actorId, false, false, true);

        assertThatThrownBy(() -> service.ownerSchedule(scheduleId, actorId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bookingRepository, never()).findByTrainingScheduleId(any());
    }

    @Test
    void createdScheduleNotifiesOnlyTheCaretakerOnTheHorseProfile() {
        UserAccount caretaker = new UserAccount("stable-staff", "stable@test.local", "encoded", Set.of(Role.STABLE_STAFF));
        ReflectionTestUtils.setField(horse, "caretaker", caretaker);
        prepareCreate(false);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(trainer));
        when(repository.saveAndFlush(any(TrainingSchedule.class))).thenAnswer(invocation -> {
            TrainingSchedule saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(bookingRepository.saveAndFlush(any(TrialRunBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(actorId, request(), false, true);

        verify(notificationService).createDeduplicated(org.mockito.ArgumentMatchers.eq(caretaker),
                org.mockito.ArgumentMatchers.eq("TRAINING_SCHEDULE_PUBLISHED"), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("TRAINING_SCHEDULE"), any(), any());
    }

    @Test
    void returnsFixedWindowAndCreatesOneLaneBooking() {
        prepareCreate(false);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(trainer));
        when(repository.saveAndFlush(any(TrainingSchedule.class))).thenAnswer(invocation -> {
            TrainingSchedule saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(bookingRepository.saveAndFlush(any(TrialRunBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(actorId, request(), false, true).getFirst();

        assertThat(created.trainingDate()).isEqualTo(TRAINING_DATE);
        assertThat(created.operatingWindowStart()).isEqualTo(java.time.LocalTime.of(5, 0));
        assertThat(created.operatingWindowEnd()).isEqualTo(java.time.LocalTime.of(11, 0));
        verify(bookingRepository).saveAndFlush(any(TrialRunBooking.class));
    }

    @Test
    void updatesPreparingSessionAndItsLaneBookingTogether() {
        UUID scheduleId = UUID.randomUUID();
        TrainingSchedule current = schedule();
        ReflectionTestUtils.setField(current, "id", scheduleId);
        TrialRunBooking booking = new TrialRunBooking(current, lane, current.getScheduledStart(), current.getScheduledEnd());
        LocalDate newDate = TRAINING_DATE.plusDays(1);
        when(repository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(current));
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(laneRepository.findDetailByIdForUpdate(laneId)).thenReturn(Optional.of(lane));
        when(bookingRepository.findByTrainingScheduleIdForUpdate(scheduleId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByTrainingScheduleId(scheduleId)).thenReturn(Optional.of(booking));

        var result = service.update(scheduleId, actorId,
                new UpdateTrainingScheduleRequest("Updated run", newDate, trackId, laneId), false, true);

        assertThat(result.taskName()).isEqualTo("Updated run");
        assertThat(result.trainingDate()).isEqualTo(newDate);
        assertThat(booking.getBookedStart()).isEqualTo(current.getScheduledStart());
        verify(repository).saveAndFlush(current);
        verify(bookingRepository).saveAndFlush(booking);
    }

    @Test
    void editsSessionAfterItIsReady() {
        UUID scheduleId = UUID.randomUUID();
        TrainingSchedule current = schedule();
        current.markReady(actorId, NOW);
        TrialRunBooking booking = new TrialRunBooking(current, lane, current.getScheduledStart(), current.getScheduledEnd());
        when(repository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(current));
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(laneRepository.findDetailByIdForUpdate(laneId)).thenReturn(Optional.of(lane));
        when(bookingRepository.findByTrainingScheduleIdForUpdate(scheduleId)).thenReturn(Optional.of(booking));

        service.update(scheduleId, actorId,
                new UpdateTrainingScheduleRequest("Updated run", TRAINING_DATE, trackId, laneId), false, true);
        assertThat(current.getTaskName()).isEqualTo("Updated run");
        assertThat(current.getSessionStatus()).isEqualTo("READY");
    }

    @Test
    void deletesPreparingSessionAndItsDependentBooking() {
        UUID scheduleId = UUID.randomUUID();
        TrainingSchedule current = schedule();
        when(repository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(current));

        service.cancel(scheduleId, actorId, false, true);

        verify(horseAccessService).requireTrainingAccess(horseId, actorId, false, true, false);
        verify(jdbc).update("delete from trial_run_bookings where training_schedule_id=?", scheduleId);
        verify(jdbc).update("delete from training_schedules where id=?", scheduleId);
    }

    @Test
    void deletesCompletedSessionAndItsEvaluation() {
        UUID scheduleId = UUID.randomUUID();
        TrainingSchedule current = schedule();
        current.finishTraining(NOW);
        when(repository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(current));

        service.cancel(scheduleId, actorId, false, true);
        verify(jdbc).update("delete from training_evaluations where training_schedule_id=?", scheduleId);
        verify(jdbc).update("delete from training_schedules where id=?", scheduleId);
    }

    @Test
    void finishTrainingCreatesRecoveryTaskForCaretaker() {
        UUID scheduleId = UUID.randomUUID();
        UUID caretakerId = UUID.randomUUID();
        UserAccount caretaker = new UserAccount("caretaker", "caretaker@test.local", "encoded", Set.of(Role.STABLE_STAFF));
        ReflectionTestUtils.setField(caretaker, "id", caretakerId);
        ReflectionTestUtils.setField(horse, "caretaker", caretaker);

        TrainingSchedule current = schedule();
        current.publish(NOW);
        current.markReady(caretakerId, NOW);
        current.startTraining(NOW);

        com.racehorse.caretask.service.CareTaskService mockCareTaskService = org.mockito.Mockito.mock(com.racehorse.caretask.service.CareTaskService.class);
        ReflectionTestUtils.setField(service, "careTaskService", mockCareTaskService);

        when(repository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(current));
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(repository.save(any(TrainingSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.finishTraining(scheduleId, actorId);

        assertThat(current.getSessionStatus()).isEqualTo("COMPLETED");
        verify(mockCareTaskService).createRecoveryTask(eq(current), eq(caretakerId));
    }


    private void prepareCreate(boolean occupiedLane) {
        when(phaseRepository.findDetailById(phaseId)).thenReturn(Optional.of(phase));
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(laneRepository.findDetailByIdForUpdate(laneId)).thenReturn(Optional.of(lane));
        when(repository.existsByHorseIdAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
                any(), any(), any())).thenReturn(false);
        when(bookingRepository.existsByLaneIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
                any(), any(), any())).thenReturn(occupiedLane);
    }

    private CreateTrainingScheduleRequest request() {
        return new CreateTrainingScheduleRequest(phaseId, trackId, laneId, "Trial run", TRAINING_DATE);
    }

    private TrainingSchedule schedule() {
        return new TrainingSchedule(phase, horse, track, "Trial run", WorkloadIntensity.MEDIUM,
                TRAINING_DATE.atTime(5, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                TRAINING_DATE.atTime(11, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), trainer);
    }
}
