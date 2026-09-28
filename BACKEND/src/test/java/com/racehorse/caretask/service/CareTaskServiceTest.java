package com.racehorse.caretask.service;

import com.racehorse.caretask.dto.CareTaskResponse;
import com.racehorse.caretask.dto.CompleteCareTaskRequest;
import com.racehorse.caretask.dto.CreateCareTaskRequest;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CareTaskServiceTest {

    private JdbcTemplate jdbc;
    private HorseAccessService horseAccess;
    private ObjectMapper json;
    private CareTaskService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        horseAccess = mock(HorseAccessService.class);
        json = new ObjectMapper();
        service = new CareTaskService(jdbc, horseAccess, json);
    }

    @Test
    void createTaskValidatesAndPersists() {
        UUID horseId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        CreateCareTaskRequest request = new CreateCareTaskRequest(
                horseId, "MUCKING_OUT", "Morning mucking",
                LocalDate.of(2026, 9, 25), LocalTime.of(8, 0), staffId);

        // Mock getTask after insert
        String dataJson = """
                {
                  "taskType": "MUCKING_OUT",
                  "title": "Morning mucking",
                  "scheduledDate": "2026-09-25",
                  "scheduledTime": "08:00",
                  "assignedStaffId": "%s"
                }
                """.formatted(staffId);

        when(jdbc.query(contains("where m.id=? and m.module_code='STABLE_CARE' and m.record_type='DAILY_CARE_TASK'"),
                any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getString("horse_code")).thenReturn("H-001");
                    when(rs.getString("horse_name")).thenReturn("Flash");
                    when(rs.getString("status")).thenReturn("PENDING");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(dataJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        CareTaskResponse response = service.createTask(actor, request);

        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.taskType()).isEqualTo("MUCKING_OUT");

        verify(horseAccess).requireActive(horseId);
        verify(jdbc).update(contains("insert into module_records"), any(), eq(horseId), eq(staffId), eq(LocalDate.of(2026, 9, 25)), anyString());
    }

    @Test
    void completeTaskUpdatesStatusAndRecordsCompletionDetails() {
        UUID taskId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        // 1. Initial task state (PENDING)
        String initialJson = """
                {
                  "taskType": "ICE_BOOTS",
                  "title": "Ice boots therapy",
                  "scheduledDate": "2026-09-25",
                  "scheduledTime": "15:00",
                  "assignedStaffId": "%s"
                }
                """.formatted(staffId);

        when(jdbc.query(contains("where m.id=? and m.module_code='STABLE_CARE' and m.record_type='DAILY_CARE_TASK'"),
                any(RowMapper.class), eq(taskId)))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(taskId);
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getString("horse_code")).thenReturn("H-001");
                    when(rs.getString("horse_name")).thenReturn("Flash");
                    when(rs.getString("status")).thenReturn("PENDING");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(initialJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.queryForObject(eq("select data_json from module_records where id=?"), eq(String.class), eq(taskId)))
                .thenReturn(initialJson);

        when(jdbc.query(eq("select username from users where id=?"), any(RowMapper.class), eq(staffId)))
                .thenReturn(List.of("groom_user"));

        CompleteCareTaskRequest completeReq = new CompleteCareTaskRequest("Applied 20 mins ice", "https://img.example.com/ice.jpg");

        service.completeTask(taskId, staffId, completeReq);

        verify(jdbc).update(contains("update module_records set status='COMPLETED'"), anyString(), eq(taskId));
    }

    @Test
    void completeTaskRejectsAlreadyCompleted() {
        UUID taskId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        when(jdbc.query(contains("where m.id=? and m.module_code='STABLE_CARE' and m.record_type='DAILY_CARE_TASK'"),
                any(RowMapper.class), eq(taskId)))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(taskId);
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("COMPLETED");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn("""
                            {"taskType":"BATHING","title":"Bath","scheduledDate":"2026-09-25","scheduledTime":"10:00"}
                            """);
                    return List.of(mapper.mapRow(rs, 1));
                });

        assertThatThrownBy(() -> service.completeTask(taskId, actor, new CompleteCareTaskRequest("Notes", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được hoàn thành trước đó");
    }

    @Test
    void getTaskThrowsWhenNotFound() {
        UUID taskId = UUID.randomUUID();
        when(jdbc.query(contains("where m.id=?"), any(RowMapper.class), eq(taskId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getTask(taskId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy nhiệm vụ chăm sóc");
    }
}
