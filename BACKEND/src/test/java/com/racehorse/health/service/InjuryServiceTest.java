package com.racehorse.health.service;

import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.AddInjuryRecoveryUpdateRequest;
import com.racehorse.health.dto.CreateInjuryMarkerRequest;
import com.racehorse.health.dto.InjuryRecoveryUpdateResponse;
import com.racehorse.health.dto.InjuryResponse;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InjuryServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private HorseAccessService horseAccessService;
    private final ObjectMapper json = new ObjectMapper();
    private InjuryService service;

    @BeforeEach
    void setUp() {
        service = new InjuryService(jdbc, horseAccessService, json);
    }

    @Test
    void createRejectsInvalidSeverity() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        CreateInjuryMarkerRequest req = new CreateInjuryMarkerRequest(
                null, "Right knee", 0.5, 1.2, -0.3, "FATAL", "ACUTE", "Notes", List.of());

        assertThatThrownBy(() -> service.createInjury(horseId, vetId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mức độ nghiêm trọng chỉ nhận");
    }

    @Test
    void createRejectsInvalidRecoveryStatus() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        CreateInjuryMarkerRequest req = new CreateInjuryMarkerRequest(
                null, "Right knee", 0.5, 1.2, -0.3, "SEVERE", "COMPLETELY_DEAD", "Notes", List.of());

        assertThatThrownBy(() -> service.createInjury(horseId, vetId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trạng thái hồi phục chỉ nhận");
    }

    @Test
    void createPersistsMarkerAndInitialRecoveryUpdate() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        CreateInjuryMarkerRequest req = new CreateInjuryMarkerRequest(
                null, "Left foreleg cannon bone", 0.35, 0.85, 0.12, "MODERATE", "ACUTE",
                "Hairline fissure detected on X-ray", List.of(fileId));

        when(jdbc.query(contains("select username from users"), any(RowMapper.class), eq(vetId)))
                .thenReturn(List.of("dr_vet"));

        InjuryResponse response = service.createInjury(horseId, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.bodyLocation()).isEqualTo("Left foreleg cannon bone");
        assertThat(response.x()).isEqualTo(0.35);
        assertThat(response.y()).isEqualTo(0.85);
        assertThat(response.z()).isEqualTo(0.12);
        assertThat(response.severity()).isEqualTo("MODERATE");
        assertThat(response.currentStatus()).isEqualTo("ACUTE");
        assertThat(response.attachmentFileIds()).containsExactly(fileId);

        verify(horseAccessService).requireActive(horseId);
        // Verify 2 inserts: marker and initial recovery timeline update
        verify(jdbc, times(2)).update(contains("insert into module_records"),
                any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void detailKeepsStructuredRegionsAndAdditionalNotes() throws Exception {
        UUID injuryId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(injuryId);
        when(row.getObject("horse_id", UUID.class)).thenReturn(horseId);
        when(row.getObject("user_id", UUID.class)).thenReturn(vetId);
        when(row.getString("horse_code")).thenReturn("H-01");
        when(row.getString("horse_name")).thenReturn("Horse");
        when(row.getString("username")).thenReturn("doctor");
        when(row.getString("data_json")).thenReturn("""
                {"bodyLocation":"Gối trước phải","bodyRegions":["FL_KNEE_RIGHT"],
                 "additionalNotes":"Mặt ngoài","severity":"MODERATE","currentStatus":"ACUTE"}
                """);
        when(row.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
        when(row.getObject("updated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
        when(jdbc.query(contains("select m.id"), any(RowMapper.class), eq(injuryId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
        when(jdbc.query(contains("select u.id"), any(RowMapper.class), eq(injuryId.toString())))
                .thenReturn(List.of());

        var detail = service.getDetail(injuryId, vetId, true);

        assertThat(detail.bodyRegions()).containsExactly("FL_KNEE_RIGHT");
        assertThat(detail.additionalNotes()).isEqualTo("Mặt ngoài");
        assertThat(detail.x()).isNull();
        verify(horseAccessService).requireReadable(horseId, vetId, true);
    }

    @Test
    void addRecoveryUpdateAppendsUpdateAndUpdatesMarkerStatus() {
        UUID injuryId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();

        when(jdbc.queryForList(contains("select id, horse_id, data_json"), eq(injuryId)))
                .thenReturn(List.of(Map.of("id", injuryId, "horse_id", horseId, "data_json", "{}")));
        when(jdbc.query(contains("select username from users"), any(RowMapper.class), eq(vetId)))
                .thenReturn(List.of("dr_vet"));

        AddInjuryRecoveryUpdateRequest req = new AddInjuryRecoveryUpdateRequest(
                "RECOVERING", "Bone callus forming nicely", List.of());

        InjuryRecoveryUpdateResponse response = service.addRecoveryUpdate(injuryId, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.injuryId()).isEqualTo(injuryId);
        assertThat(response.recoveryStatus()).isEqualTo("RECOVERING");
        assertThat(response.notes()).isEqualTo("Bone callus forming nicely");

        verify(horseAccessService).requireActive(horseId);
        // Verify insert update into module_records
        verify(jdbc).update(contains("insert into module_records"),
                any(), eq(horseId), eq(vetId), any(), any(), anyString());
        // Verify update marker's currentStatus
        verify(jdbc).update(contains("update module_records"),
                eq("RECOVERING"), eq("RECOVERING"), eq(injuryId));
    }

    @Test
    void addRecoveryUpdateThrowsWhenMarkerNotFound() {
        UUID injuryId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();

        when(jdbc.queryForList(contains("select id, horse_id, data_json"), eq(injuryId)))
                .thenReturn(List.of());

        AddInjuryRecoveryUpdateRequest req = new AddInjuryRecoveryUpdateRequest("HEALED", "Done", List.of());

        assertThatThrownBy(() -> service.addRecoveryUpdate(injuryId, vetId, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy dấu chấn thương");
    }

    @Test
    void deleteRemovesMarkerAndRecoveryUpdates() {
        UUID injuryId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(jdbc.queryForList(contains("record_type in ('INJURY_MARKER', 'INJURY')"), eq(injuryId)))
                .thenReturn(List.of(Map.of("horse_id", horseId)));

        service.delete(injuryId, actorId);

        verify(horseAccessService).requireActive(horseId);
        verify(jdbc).update(contains("record_type='INJURY_RECOVERY_UPDATE'"),
                eq(injuryId.toString()), eq(injuryId.toString()), anyString());
        verify(jdbc).update(contains("delete from module_records where id=? and module_code='MEDICAL' and record_type in ('INJURY_MARKER', 'INJURY')"),
                eq(injuryId));
    }

    @Test
    void deleteThrowsWhenInjuryNotFound() {
        UUID injuryId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(jdbc.queryForList(contains("record_type in ('INJURY_MARKER', 'INJURY')"), eq(injuryId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.delete(injuryId, actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy thông tin chấn thương");
    }
}
