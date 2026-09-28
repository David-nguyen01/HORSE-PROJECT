package com.racehorse.incident.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.incident.dto.CreateIncidentReportRequest;
import com.racehorse.incident.dto.IncidentReportResponse;
import com.racehorse.incident.dto.UpdateIncidentStatusRequest;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentReportServiceTest {

    private JdbcTemplate jdbc;
    private HorseAccessService horseAccess;
    private UserAccountRepository userRepository;
    private NotificationService notificationService;
    private ObjectMapper json;
    private Clock clock;
    private IncidentReportService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        horseAccess = mock(HorseAccessService.class);
        userRepository = mock(UserAccountRepository.class);
        notificationService = mock(NotificationService.class);
        json = new ObjectMapper();
        clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC);
        service = new IncidentReportService(jdbc, horseAccess, userRepository, notificationService, json, clock);
    }

    @Test
    void createReportDispatchesHighPriorityNotificationForCriticalIncident() {
        UUID horseId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        Horse horse = mock(Horse.class);
        when(horse.getId()).thenReturn(horseId);
        when(horse.getCode()).thenReturn("H-007");
        when(horse.getName()).thenReturn("Thunder");
        when(horseAccess.requireActive(horseId)).thenReturn(horse);

        when(jdbc.query(eq("select username from users where id=?"), any(RowMapper.class), eq(reporterId)))
                .thenReturn(List.of("groom_dan"));

        UserAccount vet = new UserAccount("vet_dr", "vet@example.com", "hash", Set.of(Role.VETERINARIAN));
        UserAccount trainer = new UserAccount("head_trainer", "trainer@example.com", "hash", Set.of(Role.HEAD_TRAINER));
        when(userRepository.findByRoles(anyCollection())).thenReturn(List.of(vet, trainer));

        CreateIncidentReportRequest request = new CreateIncidentReportRequest(
                horseId, "COLIC", "CRITICAL", "Ngựa lăn lộn liên tục, vã mồ hôi nhiều", "https://img.example.com/colic.jpg");

        // Mock getReport return after insertion
        String reportJson = """
                {
                  "incidentType": "COLIC",
                  "severity": "CRITICAL",
                  "description": "Ngựa lăn lộn liên tục, vã mồ hôi nhiều",
                  "photoUrl": "https://img.example.com/colic.jpg",
                  "reportedByUserId": "%s",
                  "reportedByName": "groom_dan",
                  "reportedAt": "2026-09-25T10:00:00Z"
                }
                """.formatted(reporterId);

        when(jdbc.query(contains("where m.id=? and m.module_code='STABLE_CARE' and m.record_type='INCIDENT_REPORT'"),
                any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<IncidentReportResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getString("horse_code")).thenReturn("H-007");
                    when(rs.getString("horse_name")).thenReturn("Thunder");
                    when(rs.getString("status")).thenReturn("REPORTED");
                    when(rs.getObject("event_date", java.time.LocalDate.class)).thenReturn(java.time.LocalDate.of(2026, 9, 25));
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(reportJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        IncidentReportResponse response = service.createReport(reporterId, request);

        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.severity()).isEqualTo("CRITICAL");
        assertThat(response.incidentType()).isEqualTo("COLIC");

        // Verify module_records insertion
        verify(jdbc).update(contains("insert into module_records"), any(), eq(horseId), eq(reporterId), any(), anyString());

        // Verify immediate notifications sent with HIGH priority to 2 recipients
        verify(notificationService, times(2)).create(
                any(UserAccount.class),
                eq("EMERGENCY_INCIDENT"),
                eq(NotificationPriority.HIGH),
                contains("Thunder"),
                contains("COLIC"),
                eq("INCIDENT_REPORT"),
                any(UUID.class));
    }

    @Test
    void createReportRejectsInvalidSeverity() {
        UUID horseId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        Horse horse = mock(Horse.class);
        when(horseAccess.requireActive(horseId)).thenReturn(horse);

        CreateIncidentReportRequest request = new CreateIncidentReportRequest(
                horseId, "COLIC", "INVALID_SEVERITY", "Test desc", null);

        assertThatThrownBy(() -> service.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mức độ nghiêm trọng (severity) không hợp lệ");
    }

    @Test
    void updateStatusToResolvedUpdatesDatabase() {
        UUID incidentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String currentJson = """
                {
                  "incidentType": "FEVER",
                  "severity": "HIGH",
                  "description": "Sốt 39.5 độ",
                  "reportedByUserId": "%s",
                  "reportedByName": "groom_1"
                }
                """.formatted(UUID.randomUUID());

        when(jdbc.query(contains("where m.id=? and m.module_code='STABLE_CARE' and m.record_type='INCIDENT_REPORT'"),
                any(RowMapper.class), eq(incidentId)))
                .thenAnswer(inv -> {
                    RowMapper<IncidentReportResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(incidentId);
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("horse_code")).thenReturn("H-001");
                    when(rs.getString("horse_name")).thenReturn("Flash");
                    when(rs.getString("status")).thenReturn("UNDER_REVIEW");
                    when(rs.getObject("event_date", java.time.LocalDate.class)).thenReturn(java.time.LocalDate.of(2026, 9, 25));
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(currentJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.queryForObject(eq("select data_json from module_records where id=?"), eq(String.class), eq(incidentId)))
                .thenReturn(currentJson);

        when(jdbc.query(eq("select username from users where id=?"), any(RowMapper.class), eq(actorId)))
                .thenReturn(List.of("dr_vet"));

        UpdateIncidentStatusRequest updateReq = new UpdateIncidentStatusRequest("RESOLVED", "Đã tiêm hạ sốt và dịch truyền, nhiệt độ hạ còn 38.2");

        service.updateStatus(incidentId, actorId, updateReq);

        verify(jdbc).update(contains("update module_records set status=?, data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?"),
                eq("RESOLVED"), anyString(), eq(incidentId));
    }

    @Test
    void getReportThrowsWhenNotFound() {
        UUID incidentId = UUID.randomUUID();
        when(jdbc.query(contains("where m.id=?"), any(RowMapper.class), eq(incidentId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getReport(incidentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy báo cáo sự cố");
    }
}
