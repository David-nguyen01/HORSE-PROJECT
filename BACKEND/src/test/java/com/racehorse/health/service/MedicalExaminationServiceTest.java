package com.racehorse.health.service;

import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.AddMedicalExaminationAddendumRequest;
import com.racehorse.health.dto.CreateMedicalExaminationRequest;
import com.racehorse.health.dto.CreatePreventiveCareRequest;
import com.racehorse.health.dto.MedicalExaminationAddendumResponse;
import com.racehorse.health.dto.MedicalExaminationResponse;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.medical.service.FollowUpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicalExaminationServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private HorseAccessService horseAccessService;
    @Mock private TrainingLockRepository trainingLockRepository;
    @Mock private TrainingLockService trainingLockService;
    @Mock private PreventiveCareService preventiveCareService;
    @Mock private UserAccountRepository userRepository;
    @Mock private NotificationService notificationService;
    private final ObjectMapper json = new ObjectMapper();
    private MedicalExaminationService service;
    private Horse existingHorse;

    @BeforeEach
    void setUp() {
        service = new MedicalExaminationService(
                jdbc, horseAccessService, trainingLockRepository, trainingLockService,
                preventiveCareService, userRepository, notificationService, json);
        existingHorse = mock(Horse.class);
        lenient().when(existingHorse.getIntakeStatus()).thenReturn("STABLED");
        lenient().when(horseAccessService.requireActive(any(UUID.class))).thenReturn(existingHorse);
    }

    private void assignOriginalVeterinarian(UUID veterinarianId) {
        UserAccount originalVet = mock(UserAccount.class);
        when(originalVet.getId()).thenReturn(veterinarianId);
        when(existingHorse.getDiagnosingVet()).thenReturn(originalVet);
    }

    @Test
    void createRejectsFutureExamDate() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now().plusDays(2), "Cough", "Equine Flu",
                "Rest and antibiotics", "Twice daily", null, "MONITOR");

        assertThatThrownBy(() -> service.create(horseId, vetId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được ở tương lai");
    }

    @Test
    void createRejectsFollowUpBeforeExamDate() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Cough", "Equine Flu",
                "Rest and antibiotics", "Twice daily",
                LocalDate.now().minusDays(1), "MONITOR");

        assertThatThrownBy(() -> service.create(horseId, vetId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngày tái khám phải sau ngày khám");
    }

    @Test
    void createRejectsFollowUpOnTheSameDayAsExam() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        LocalDate examDate = LocalDate.now();
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                examDate, "Kiểm tra", "Theo dõi", "Nghỉ ngơi", null, examDate, "MONITOR");

        assertThatThrownBy(() -> service.create(horseId, vetId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngày tái khám phải sau ngày khám");
    }

    @Test
    void createRejectsInvalidHealthStatus() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Cough", "Equine Flu",
                "Rest and antibiotics", "Twice daily",
                LocalDate.now().plusDays(7), "INVALID_STATUS");

        assertThatThrownBy(() -> service.create(horseId, vetId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trạng thái sức khỏe chỉ nhận");
    }

    @Test
    void createRejectsHorseNotYetReceivedByAnyVeterinarian() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        Horse waiting = mock(Horse.class);
        when(waiting.getIntakeStatus()).thenReturn("WAITING_FOR_VET");
        when(horseAccessService.requireActive(horseId)).thenReturn(waiting);
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Check", "Normal", "Observe", null, null, "FIT");

        assertThatThrownBy(() -> service.create(horseId, vetId, req))
                .isInstanceOf(BusinessException.class).hasMessageContaining("tiếp nhận");
        verify(jdbc, never()).update(contains("MEDICAL_EXAMINATION"), any(Object[].class));
    }

    @Test
    void createRejectsVeterinarianWhoDidNotReceiveActiveCase() {
        UUID horseId = UUID.randomUUID();
        UUID otherVet = UUID.randomUUID();
        Horse inMedical = mock(Horse.class);
        when(inMedical.getIntakeStatus()).thenReturn("IN_MEDICAL");
        when(horseAccessService.requireActive(horseId)).thenReturn(inMedical);
        when(jdbc.queryForObject(contains("UPDLOCK,HOLDLOCK"), eq(UUID.class), eq(horseId)))
                .thenReturn(horseId);
        when(jdbc.queryForObject(contains("MEDICAL_ASSIGNMENT"), eq(Integer.class), eq(horseId), eq(otherVet)))
                .thenReturn(0);
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Check", "Normal", "Observe", null, null, "FIT");

        assertThatThrownBy(() -> service.create(horseId, otherVet, req))
                .isInstanceOf(BusinessException.class).hasMessageContaining("bác sĩ khác");
    }

    @Test
    void createRejectsOtherDoctorWhileFollowUpIsAssigned() {
        UUID horseId = UUID.randomUUID();
        UUID otherVet = UUID.randomUUID();
        FollowUpService followUps = mock(FollowUpService.class);
        MedicalExaminationService protectedService = new MedicalExaminationService(
                jdbc, horseAccessService, trainingLockRepository, trainingLockService,
                preventiveCareService, userRepository, notificationService, followUps, json);
        when(followUps.requireAssignedForExamination(horseId, otherVet))
                .thenThrow(new BusinessException("Ca tái khám do bác sĩ khác tiếp nhận"));
        CreateMedicalExaminationRequest request = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Kiểm tra", "Theo dõi", "Nghỉ ngơi", null, null, "MONITOR");

        assertThatThrownBy(() -> protectedService.create(horseId, otherVet, request))
                .isInstanceOf(BusinessException.class).hasMessageContaining("bác sĩ khác");
        verify(jdbc, never()).update(contains("insert into module_records"), any(Object[].class));
    }

    @Test
    void createRejectsOtherDoctorAfterInitialExamWhenNoFollowUpIsOpen() {
        UUID horseId = UUID.randomUUID();
        UUID originalVetId = UUID.randomUUID();
        UUID otherVetId = UUID.randomUUID();
        assignOriginalVeterinarian(originalVetId);
        CreateMedicalExaminationRequest request = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Kiểm tra", "Theo dõi", "Nghỉ ngơi", null, null, "MONITOR");

        assertThatThrownBy(() -> service.create(horseId, otherVetId, request))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Chỉ bác sĩ phụ trách");
        verify(jdbc, never()).update(contains("insert into module_records"), any(Object[].class));
    }

    @Test
    void acceptedFollowUpDoctorMayExamineWithoutChangingPrimaryVetHere() {
        UUID horseId = UUID.randomUUID();
        UUID newVetId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        FollowUpService followUps = mock(FollowUpService.class);
        MedicalExaminationService protectedService = new MedicalExaminationService(
                jdbc, horseAccessService, trainingLockRepository, trainingLockService,
                preventiveCareService, userRepository, notificationService, followUps, json);
        when(followUps.requireAssignedForExamination(horseId, newVetId)).thenReturn(followUpId);
        when(jdbc.query(contains("select username from users"), any(RowMapper.class), eq(newVetId)))
                .thenReturn(List.of("new_vet"));
        CreateMedicalExaminationRequest request = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Tái khám", "Ổn định", "Theo dõi", null, null, "FIT");

        protectedService.create(horseId, newVetId, request);

        verify(followUps).recordExamination(eq(horseId), eq(newVetId), any(UUID.class),
                eq(followUpId), eq(request.examinationDate()), isNull());
        verify(jdbc, never()).update(contains("set diagnosing_vet_user_id=?"), any(), any());
    }

    @Test
    void createPersistsExaminationAndUpdatesHorseHealthStatus() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        assignOriginalVeterinarian(vetId);
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Lameness in left hind", "Sprain",
                "Cold compress and rest", "Anti-inflammatory 10mg",
                LocalDate.now().plusDays(5), "INJURED");

        when(jdbc.query(contains("select username from users"), any(RowMapper.class), eq(vetId)))
                .thenReturn(List.of("dr_vet"));

        MedicalExaminationResponse response = service.create(horseId, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.veterinarianId()).isEqualTo(vetId);
        assertThat(response.veterinarianName()).isEqualTo("dr_vet");
        assertThat(response.symptoms()).isEqualTo("Lameness in left hind");
        assertThat(response.healthStatusAfter()).isEqualTo("INJURED");

        verify(horseAccessService).requireActive(horseId);
        // Verify insert into module_records
        verify(jdbc).update(contains("insert into module_records"),
                any(), eq(horseId), eq(vetId), eq(req.examinationDate()), anyString());
        // Verify update health_status on horses
        verify(jdbc).update(contains("update horses"),
                eq("INJURED"), eq(vetId), any(), eq(horseId));
    }

    @Test
    void createDoesNotDuplicateLegacyPreventiveSchedule() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        assignOriginalVeterinarian(vetId);
        CreatePreventiveCareRequest care = new CreatePreventiveCareRequest(
                "VACCINATION", "Routine vaccine", LocalDate.now().plusDays(7), null, 3);
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Check", "Normal", "Observe", null, null, "FIT",
                care, List.of(care));
        service.create(horseId, vetId, req);
        verify(preventiveCareService, times(1)).create(horseId, vetId, care);
    }

    @Test
    void addAddendumAppendsAndUpdatesHealthStatus() {
        UUID examId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        assignOriginalVeterinarian(vetId);

        when(jdbc.queryForList(contains("select id, horse_id"), eq(examId)))
                .thenReturn(List.of(Map.of("id", examId, "horse_id", horseId)));
        when(jdbc.query(contains("select username from users"), any(RowMapper.class), eq(vetId)))
                .thenReturn(List.of("dr_vet"));

        AddMedicalExaminationAddendumRequest req = new AddMedicalExaminationAddendumRequest(
                "Swelling reduced significantly. Moving horse to monitor.", "MONITOR");

        MedicalExaminationAddendumResponse response = service.addAddendum(examId, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.examinationId()).isEqualTo(examId);
        assertThat(response.notes()).contains("Swelling reduced");
        assertThat(response.healthStatusAfter()).isEqualTo("MONITOR");

        verify(horseAccessService).requireActive(horseId);
        // Verify insert addendum into module_records
        verify(jdbc).update(contains("insert into module_records"),
                any(), eq(horseId), eq(vetId), any(), anyString());
        // Verify horse health_status update
        verify(jdbc).update(contains("update horses"),
                eq("MONITOR"), eq(vetId), any(), eq(horseId));
    }

    @Test
    void addAddendumThrowsWhenExamNotFound() {
        UUID examId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();

        when(jdbc.queryForList(contains("select id, horse_id"), eq(examId)))
                .thenReturn(List.of());

        AddMedicalExaminationAddendumRequest req = new AddMedicalExaminationAddendumRequest("Notes", null);

        assertThatThrownBy(() -> service.addAddendum(examId, vetId, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy hồ sơ khám bệnh");
    }

    @Test
    void createWithPreventiveSchedulesAndAlias() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        assignOriginalVeterinarian(vetId);
        CreatePreventiveCareRequest sched1 = new CreatePreventiveCareRequest("VACCINATION", "Flu shot", LocalDate.now().plusDays(10), null, 3);
        CreatePreventiveCareRequest sched2 = new CreatePreventiveCareRequest("DEWORMING", "Ivermectin", LocalDate.now().plusDays(20), null, 2);

        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now(), "General checkup", "Normal routine",
                "Maintain regimen", null, null, "HEALTHY",
                null, List.of(sched1, sched2));

        when(jdbc.query(contains("select username from users"), any(RowMapper.class), eq(vetId)))
                .thenReturn(List.of("dr_vet"));
        MedicalExaminationResponse response = service.create(horseId, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.healthStatusAfter()).isEqualTo("FIT");
        verify(preventiveCareService).create(horseId, vetId, sched1);
        verify(preventiveCareService).create(horseId, vetId, sched2);
        verify(jdbc).update(contains("update horses"), eq("FIT"), eq(vetId), any(), eq(horseId));
    }

    @Test
    void deleteRemovesExamAndRevertsHorseHealthStatus() {
        UUID examId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(jdbc.queryForList(contains("select horse_id from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'"), eq(examId)))
                .thenReturn(List.of(Map.of("horse_id", horseId)));
        when(jdbc.queryForList(contains("select health_source_id from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of("health_source_id", examId)));
        // No remaining exams -> resets to FIT
        when(jdbc.queryForList(contains("order by event_date desc, created_at desc"), eq(horseId)))
                .thenReturn(List.of());

        service.delete(examId, actorId);

        verify(horseAccessService).requireActive(horseId);
        verify(jdbc).update(contains("'$.sourceExaminationDeleted',1"), eq(examId.toString()));
        verify(jdbc).update(contains("record_type='MEDICAL_EXAMINATION_ADDENDUM'"), eq(examId.toString()));
        verify(jdbc).update(contains("delete from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'"), eq(examId));
        verify(jdbc).update(contains("set health_status = 'FIT'"), eq(actorId), eq(horseId));
    }

    @Test
    void deleteThrowsWhenExamNotFound() {
        UUID examId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(jdbc.queryForList(contains("select horse_id from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'"), eq(examId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.delete(examId, actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy hồ sơ khám bệnh");
    }

    @Test
    void deleteAddendumRemovesAddendumAndRevertsHorseStatus() {
        UUID addendumId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(jdbc.queryForList(contains("record_type='MEDICAL_EXAMINATION_ADDENDUM'"), eq(addendumId)))
                .thenReturn(List.of(Map.of("horse_id", horseId)));
        when(jdbc.queryForList(contains("select health_source_id from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of("health_source_id", addendumId)));
        when(jdbc.queryForList(contains("order by event_date desc, created_at desc"), eq(horseId)))
                .thenReturn(List.of());

        service.deleteAddendum(addendumId, actorId);

        verify(horseAccessService).requireActive(horseId);
        verify(jdbc).update(contains("delete from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION_ADDENDUM'"), eq(addendumId));
        verify(jdbc).update(contains("set health_status = 'FIT'"), eq(actorId), eq(horseId));
    }

    @Test
    void createWithBiometricsPersistsAndReturnsAllBiometrics() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        assignOriginalVeterinarian(vetId);
        CreateMedicalExaminationRequest req = new CreateMedicalExaminationRequest(
                LocalDate.now(), "Tachypnea", "Mild Heat Stress",
                "Hydration and shade", "Electrolytes",
                LocalDate.now().plusDays(2), "MONITOR",
                null, List.of(),
                48, 185, new java.math.BigDecimal("38.5"), 162, new java.math.BigDecimal("480.5"));

        when(jdbc.query(contains("select username from users"), any(RowMapper.class), eq(vetId)))
                .thenReturn(List.of("dr_vet"));

        MedicalExaminationResponse response = service.create(horseId, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.heartRateBpm()).isEqualTo(48);
        assertThat(response.bodyLengthCm()).isEqualTo(185);
        assertThat(response.temperatureCelsius()).isEqualByComparingTo("38.5");
        assertThat(response.heightCm()).isEqualTo(162);
        assertThat(response.weightKg()).isEqualByComparingTo("480.5");

        verify(jdbc).update(contains("update horses set weight_kg=?"),
                eq(new java.math.BigDecimal("480.5")), any(), eq(vetId), anyString(), eq(horseId));
    }
}
