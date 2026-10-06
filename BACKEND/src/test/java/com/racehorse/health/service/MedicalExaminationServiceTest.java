package com.racehorse.health.service;

import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.AddMedicalExaminationAddendumRequest;
import com.racehorse.health.dto.CreateMedicalExaminationRequest;
import com.racehorse.health.dto.MedicalExaminationAddendumResponse;
import com.racehorse.health.dto.MedicalExaminationResponse;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

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
    private final ObjectMapper json = new ObjectMapper();
    private MedicalExaminationService service;

    @BeforeEach
    void setUp() {
        service = new MedicalExaminationService(jdbc, horseAccessService, json);
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
                .hasMessageContaining("Ngày tái khám phải sau hoặc bằng");
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
    void createPersistsExaminationAndUpdatesHorseHealthStatus() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
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
                eq("INJURED"), eq("INJURED"), eq(vetId), any(), eq(horseId));
    }

    @Test
    void addAddendumAppendsAndUpdatesHealthStatus() {
        UUID examId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();

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
                eq("MONITOR"), eq("MONITOR"), eq(vetId), any(), eq(horseId));
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
}
