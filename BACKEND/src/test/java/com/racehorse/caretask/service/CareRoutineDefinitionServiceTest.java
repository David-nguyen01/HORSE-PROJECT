package com.racehorse.caretask.service;

import com.racehorse.caretask.dto.*;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Time;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CareRoutineDefinitionServiceTest {

    private JdbcTemplate jdbc;
    private CareRoutineDefinitionService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new CareRoutineDefinitionService(jdbc);
    }

    @Test
    void listActiveReturnsOnlyActiveRoutinesOrdered() {
        UUID id = UUID.randomUUID();
        when(jdbc.query(contains("WHERE is_active = 1"), any(RowMapper.class)))
                .thenAnswer(inv -> {
                    RowMapper<CareRoutineDefinitionResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(id);
                    when(rs.getString("code")).thenReturn("ROUTINE_ACTIVE_01");
                    when(rs.getString("title")).thenReturn("Quy trình đang hoạt động");
                    when(rs.getString("description")).thenReturn("Mô tả hoạt động");
                    when(rs.getString("task_type")).thenReturn("STABLE_CLEANING");
                    when(rs.getTime("scheduled_time")).thenReturn(Time.valueOf(LocalTime.of(7, 0)));
                    when(rs.getString("scope")).thenReturn("FACILITY");
                    when(rs.getBoolean("is_active")).thenReturn(true);
                    when(rs.getInt("sort_order")).thenReturn(10);
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getObject("updated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    return List.of(mapper.mapRow(rs, 1));
                });

        List<CareRoutineDefinitionResponse> active = service.listActive();
        assertThat(active).hasSize(1);
        assertThat(active.getFirst().id()).isEqualTo(id);
        assertThat(active.getFirst().isActive()).isTrue();
    }

    @Test
    void listReserveReturnsOnlyReserveRoutinesOrdered() {
        UUID id = UUID.randomUUID();
        when(jdbc.query(contains("WHERE is_active = 0"), any(RowMapper.class)))
                .thenAnswer(inv -> {
                    RowMapper<CareRoutineDefinitionResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(id);
                    when(rs.getString("code")).thenReturn("ROUTINE_RESERVE_01");
                    when(rs.getString("title")).thenReturn("Quy trình dự phòng");
                    when(rs.getString("description")).thenReturn("Mô tả dự phòng");
                    when(rs.getString("task_type")).thenReturn("FACILITY_CHECK");
                    when(rs.getTime("scheduled_time")).thenReturn(Time.valueOf(LocalTime.of(14, 0)));
                    when(rs.getString("scope")).thenReturn("FACILITY");
                    when(rs.getBoolean("is_active")).thenReturn(false);
                    when(rs.getInt("sort_order")).thenReturn(20);
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getObject("updated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    return List.of(mapper.mapRow(rs, 1));
                });

        List<CareRoutineDefinitionResponse> reserve = service.listReserve();
        assertThat(reserve).hasSize(1);
        assertThat(reserve.getFirst().id()).isEqualTo(id);
        assertThat(reserve.getFirst().isActive()).isFalse();
    }

    @Test
    void getThrowsResourceNotFoundExceptionWhenMissing() {
        UUID id = UUID.randomUUID();
        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void createInReserveRejectsIfIsActiveIsTrue() {
        UUID actorId = UUID.randomUUID();
        CreateCareRoutineDefinitionRequest req = new CreateCareRoutineDefinitionRequest(
                "ROUTINE_ACTIVE_TEST", "Tạo trực tiếp Active", "Mô tả", "CLEANING",
                LocalTime.of(8, 0), "FACILITY", 10, true
        );

        assertThatThrownBy(() -> service.createInReserve(actorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Thư viện dự phòng");

        verify(jdbc, never()).update(startsWith("INSERT INTO dbo.care_routine_definitions"), any(), any());
    }

    @Test
    void createInReserveRejectsDuplicateCode() {
        UUID actorId = UUID.randomUUID();
        CreateCareRoutineDefinitionRequest req = new CreateCareRoutineDefinitionRequest(
                "ROUTINE_DUP", "Kiểm tra trùng mã", "Mô tả", "CHECK",
                LocalTime.of(9, 0), "FACILITY", 10, false
        );

        when(jdbc.queryForObject(contains("WHERE code = ?"), eq(Integer.class), eq("ROUTINE_DUP")))
                .thenReturn(1);

        assertThatThrownBy(() -> service.createInReserve(actorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    void createInReserveCreatesWithIsActiveZeroAndAutoGeneratedCode() {
        UUID actorId = UUID.randomUUID();
        CreateCareRoutineDefinitionRequest req = new CreateCareRoutineDefinitionRequest(
                null, "Kiểm tra cửa và khóa chuồng", "Mô tả bảo trì chuồng", "FACILITY_CHECK",
                LocalTime.of(15, 0), null, null, null
        );

        when(jdbc.queryForObject(contains("MAX(sort_order)"), eq(Integer.class))).thenReturn(30);

        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<CareRoutineDefinitionResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("code")).thenReturn("ROUTINE_KIEM_TRA_CUA_VA_KHOA_CHUONG_1234ABCD");
                    when(rs.getString("title")).thenReturn(req.title());
                    when(rs.getString("description")).thenReturn(req.description());
                    when(rs.getString("task_type")).thenReturn(req.taskType());
                    when(rs.getTime("scheduled_time")).thenReturn(Time.valueOf(req.scheduledTime()));
                    when(rs.getString("scope")).thenReturn("FACILITY");
                    when(rs.getBoolean("is_active")).thenReturn(false);
                    when(rs.getInt("sort_order")).thenReturn(40);
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getObject("updated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    return List.of(mapper.mapRow(rs, 1));
                });

        CareRoutineDefinitionResponse created = service.createInReserve(actorId, req);

        assertThat(created).isNotNull();
        assertThat(created.isActive()).isFalse();

        // Verify that INSERT statement inserts is_active = 0
        verify(jdbc).update(
                contains("INSERT INTO dbo.care_routine_definitions"),
                any(UUID.class),
                matches("^ROUTINE_.*"),
                eq(req.title()),
                eq(req.description()),
                eq("FACILITY_CHECK"),
                eq(Time.valueOf(req.scheduledTime())),
                eq("FACILITY"),
                eq(40)
        );
    }

    @Test
    void updateEditsFieldsWhilePreservingIsActive() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UpdateCareRoutineDefinitionRequest req = new UpdateCareRoutineDefinitionRequest(
                "Tiêu đề mới", "Mô tả mới", "FEEDING", LocalTime.of(11, 30), "FACILITY", 50
        );

        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<CareRoutineDefinitionResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(id);
                    when(rs.getString("code")).thenReturn("ROUTINE_TEST");
                    when(rs.getString("title")).thenReturn("Tiêu đề cũ");
                    when(rs.getString("scope")).thenReturn("FACILITY");
                    when(rs.getBoolean("is_active")).thenReturn(true);
                    when(rs.getInt("sort_order")).thenReturn(10);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.update(contains("SET title = ?"), any(Object[].class)))
                .thenReturn(1);

        service.update(id, actorId, req);

        // Verify update touched definition table, not care_tasks
        verify(jdbc).update(
                contains("SET title = ?"),
                eq("Tiêu đề mới"),
                eq("Mô tả mới"),
                eq("FEEDING"),
                eq(Time.valueOf(LocalTime.of(11, 30))),
                eq("FACILITY"),
                eq(50),
                eq(id)
        );
        verify(jdbc, never()).update(contains("dbo.care_tasks"), any(), any());
    }

    @Test
    void transferMovesBetweenActiveAndReserveWithoutDuplicatingOrDeletingTasks() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        // Currently Active
        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<CareRoutineDefinitionResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(id);
                    when(rs.getBoolean("is_active")).thenReturn(true);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.update(contains("SET is_active = ?"), any(Object[].class)))
                .thenReturn(1);

        // Transfer to Reserve (active = false)
        service.transfer(id, actorId, false);

        verify(jdbc).update(contains("SET is_active = ?"), eq(0), eq(id));
        // Verify historical tasks are completely untouched
        verify(jdbc, never()).update(contains("dbo.care_tasks"), any(), any());
    }

    @Test
    void transferIsIdempotentWhenAlreadyInTargetState() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        // Already Active
        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<CareRoutineDefinitionResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(id);
                    when(rs.getBoolean("is_active")).thenReturn(true);
                    return List.of(mapper.mapRow(rs, 1));
                });

        CareRoutineDefinitionResponse res = service.transfer(id, actorId, true);

        assertThat(res.isActive()).isTrue();
        // Zero update calls executed
        verify(jdbc, never()).update(contains("SET is_active = ?"), any(), any());
    }

    @Test
    void deletePermanentlyRemovesDefinitionAndSafelyUnlinksHistoricalTasks() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<CareRoutineDefinitionResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(id);
                    when(rs.getBoolean("is_active")).thenReturn(true);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.update(contains("UPDATE dbo.care_tasks SET routine_definition_id = NULL WHERE routine_definition_id = ?"), eq(id)))
                .thenReturn(5); // 5 historical tasks unlinked
        when(jdbc.update(contains("DELETE FROM dbo.care_routine_definitions WHERE id = ?"), eq(id)))
                .thenReturn(1);

        service.delete(id, actorId);

        // 1. Historical tasks unlinked (foreign key safely set to NULL without deleting task rows)
        verify(jdbc).update(
                contains("UPDATE dbo.care_tasks SET routine_definition_id = NULL WHERE routine_definition_id = ?"),
                eq(id)
        );

        // 2. Definition physically deleted from dbo.care_routine_definitions
        verify(jdbc).update(
                contains("DELETE FROM dbo.care_routine_definitions WHERE id = ?"),
                eq(id)
        );

        // 3. Ensure NO DELETE on dbo.care_tasks occurred
        verify(jdbc, never()).update(contains("DELETE FROM dbo.care_tasks"), any(), any());
    }

    @Test
    void reorderUpdatesSortOrdersSequentially() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(jdbc.update(contains("UPDATE dbo.care_routine_definitions SET sort_order = ?"), any(Object[].class)))
                .thenReturn(1);

        service.reorder(actorId, List.of(id1, id2, id3));

        verify(jdbc).update(contains("sort_order = ?"), eq(10), eq(id1));
        verify(jdbc).update(contains("sort_order = ?"), eq(20), eq(id2));
        verify(jdbc).update(contains("sort_order = ?"), eq(30), eq(id3));
    }

    @Test
    void deletePermanentlyFromReserveRemovesRowAndPreservesZeroTaskImpact() {
        UUID reserveId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(jdbc.query(contains("WHERE id = ?"), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<CareRoutineDefinitionResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(reserveId);
                    when(rs.getBoolean("is_active")).thenReturn(false);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.update(contains("UPDATE dbo.care_tasks SET routine_definition_id = NULL WHERE routine_definition_id = ?"), eq(reserveId)))
                .thenReturn(0); // Reserve task typically has 0 generated instances
        when(jdbc.update(contains("DELETE FROM dbo.care_routine_definitions WHERE id = ?"), eq(reserveId)))
                .thenReturn(1);

        service.delete(reserveId, actorId);

        verify(jdbc).update(contains("UPDATE dbo.care_tasks SET routine_definition_id = NULL WHERE routine_definition_id = ?"), eq(reserveId));
        verify(jdbc).update(contains("DELETE FROM dbo.care_routine_definitions WHERE id = ?"), eq(reserveId));
        verify(jdbc, never()).update(contains("DELETE FROM dbo.care_tasks"), any(), any());
    }

    @Test
    void dailyTaskGenerationQueryStrictlyExcludesReserveDefinitions() {
        // Verification of CareTaskService ensureRoutineInstancesForDate SQL invariant:
        // CareTaskService lines 46-47 enforces 'WHERE d.is_active = 1', guaranteeing
        // that reserve definitions (is_active = 0) are never generated as daily tasks.
        CareTaskService careTaskService = new CareTaskService(jdbc, mock(com.racehorse.horse.service.HorseAccessService.class));
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 11);

        careTaskService.ensureRoutineInstancesForDate(today);

        verify(jdbc).update(
                argThat(sql -> sql.contains("FROM dbo.care_routine_definitions d")
                        && sql.contains("WHERE d.is_active = 1")),
                eq(java.sql.Date.valueOf(today)),
                eq(java.sql.Date.valueOf(today))
        );
    }
}
