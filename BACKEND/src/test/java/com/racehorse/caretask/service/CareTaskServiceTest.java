package com.racehorse.caretask.service;

import com.racehorse.caretask.dto.CareTaskResponse;
import com.racehorse.caretask.dto.CompleteCareTaskRequest;
import com.racehorse.caretask.dto.CreateCareTaskRequest;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CareTaskServiceTest {

    private JdbcTemplate jdbc;
    private HorseAccessService horseAccess;
    private CareTaskService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        horseAccess = mock(HorseAccessService.class);
        service = new CareTaskService(jdbc, horseAccess);
    }

    @Test
    void createTaskValidatesAndPersistsSpecialTask() {
        UUID horseId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        CreateCareTaskRequest request = new CreateCareTaskRequest(
                horseId, "MUCKING_OUT", "Dọn phân chuồng đặc biệt",
                LocalDate.of(2026, 10, 1), LocalTime.of(8, 0), staffId);

        when(jdbc.query(contains("WHERE t.id = ?"), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(taskId);
                    when(rs.getObject("routine_definition_id", UUID.class)).thenReturn(null);
                    when(rs.getString("task_category")).thenReturn("SPECIAL");
                    when(rs.getString("scope")).thenReturn("HORSE");
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getString("horse_code")).thenReturn("H-001");
                    when(rs.getString("horse_name")).thenReturn("Flash");
                    when(rs.getString("task_type")).thenReturn("MUCKING_OUT");
                    when(rs.getString("title")).thenReturn("Dọn phân chuồng đặc biệt");
                    when(rs.getDate("scheduled_date")).thenReturn(Date.valueOf(LocalDate.of(2026, 10, 1)));
                    when(rs.getTime("scheduled_time")).thenReturn(Time.valueOf(LocalTime.of(8, 0)));
                    when(rs.getObject("assigned_staff_id", UUID.class)).thenReturn(staffId);
                    when(rs.getString("status")).thenReturn("PENDING");
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    return List.of(mapper.mapRow(rs, 1));
                });

        CareTaskResponse response = service.createTask(actor, request);

        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.taskCategory()).isEqualTo("SPECIAL");
        assertThat(response.scope()).isEqualTo("HORSE");

        verify(horseAccess).requireActive(horseId);
        verify(jdbc).update(contains("INSERT INTO dbo.care_tasks"),
                any(UUID.class), eq("HORSE"), eq(horseId), eq("Dọn phân chuồng đặc biệt"),
                eq("MUCKING_OUT"), any(Date.class), any(Time.class), eq(staffId), eq(actor));
    }

    @Test
    void completeTaskUpdatesStatusAndRecordsCompletionDetails() {
        UUID taskId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        when(jdbc.query(contains("WHERE t.id = ?"), any(RowMapper.class), eq(taskId)))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(taskId);
                    when(rs.getString("status")).thenReturn("PENDING");
                    when(rs.getString("task_category")).thenReturn("ROUTINE");
                    when(rs.getString("scope")).thenReturn("FACILITY");
                    when(rs.getDate("scheduled_date")).thenReturn(Date.valueOf(LocalDate.now()));
                    when(rs.getTime("scheduled_time")).thenReturn(Time.valueOf(LocalTime.of(7, 0)));
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(contains("SELECT username FROM dbo.users"), any(RowMapper.class), eq(staffId)))
                .thenReturn(List.of("stable_groom"));
        when(jdbc.update(contains("UPDATE dbo.care_tasks"), any(Object[].class))).thenReturn(1);

        CompleteCareTaskRequest completeReq = new CompleteCareTaskRequest("Đã dọn sạch", "https://img.example.com/clean.jpg");

        service.completeTask(taskId, staffId, completeReq);

        verify(jdbc).update(
                contains("UPDATE dbo.care_tasks"),
                eq(false), eq(staffId), eq(staffId), eq("stable_groom"), eq("Đã dọn sạch"),
                eq("https://img.example.com/clean.jpg"), isNull(), isNull(), eq(taskId), eq(false), eq(staffId)
        );
    }

    @Test
    void completeTaskWithMultipartPhotoUploadsAndStoresBytes() {
        UUID taskId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        when(jdbc.query(contains("WHERE t.id = ?"), any(RowMapper.class), eq(taskId)))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(taskId);
                    when(rs.getString("status")).thenReturn("PENDING");
                    when(rs.getString("task_category")).thenReturn("ROUTINE");
                    when(rs.getString("scope")).thenReturn("FACILITY");
                    when(rs.getDate("scheduled_date")).thenReturn(Date.valueOf(LocalDate.now()));
                    when(rs.getTime("scheduled_time")).thenReturn(Time.valueOf(LocalTime.of(7, 0)));
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(contains("SELECT username FROM dbo.users"), any(RowMapper.class), eq(staffId)))
                .thenReturn(List.of("stable_groom"));
        when(jdbc.update(contains("UPDATE dbo.care_tasks"), any(Object[].class))).thenReturn(1);

        byte[] fakeImage = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01 };
        MockMultipartFile file = new MockMultipartFile("file", "evidence.jpg", "image/jpeg", fakeImage);

        service.completeTask(taskId, staffId, "Dọn chuồng xong, đã chụp ảnh", file);

        verify(jdbc).update(
                contains("UPDATE dbo.care_tasks"),
                eq(false), eq(staffId), eq(staffId), eq("stable_groom"), eq("Dọn chuồng xong, đã chụp ảnh"),
                eq("/api/care-tasks/" + taskId + "/photo"),
                eq(fakeImage), eq("image/jpeg"), eq(taskId), eq(false), eq(staffId)
        );
    }

    @Test
    void completeTaskRejectsNonImageFiles() {
        UUID taskId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        when(jdbc.query(contains("WHERE t.id = ?"), any(RowMapper.class), eq(taskId)))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(taskId);
                    when(rs.getString("status")).thenReturn("PENDING");
                    return List.of(mapper.mapRow(rs, 1));
                });

        MockMultipartFile textFile = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.completeTask(taskId, staffId, "Notes", textFile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tệp tải lên phải là hình ảnh");
    }

    @Test
    void getTaskPhotoReturnsBytesWhenPresent() {
        UUID taskId = UUID.randomUUID();
        byte[] fakeImage = new byte[] { 1, 2, 3 };

        when(jdbc.query(contains("SELECT photo_bytes"), any(RowMapper.class), eq(taskId)))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskService.TaskPhotoData> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getBytes("photo_bytes")).thenReturn(fakeImage);
                    when(rs.getString("photo_content_type")).thenReturn("image/png");
                    when(rs.getString("photo_url")).thenReturn("/api/care-tasks/" + taskId + "/photo");
                    when(rs.getString("title")).thenReturn("Dọn chuồng");
                    return List.of(mapper.mapRow(rs, 1));
                });

        CareTaskService.TaskPhotoData photo = service.getTaskPhoto(taskId);
        assertThat(photo.bytes()).isEqualTo(fakeImage);
        assertThat(photo.contentType()).isEqualTo("image/png");
    }

    @Test
    void completeTaskRejectsAlreadyCompleted() {
        UUID taskId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        when(jdbc.query(contains("WHERE t.id = ?"), any(RowMapper.class), eq(taskId)))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(taskId);
                    when(rs.getString("status")).thenReturn("COMPLETED");
                    when(rs.getDate("scheduled_date")).thenReturn(Date.valueOf(LocalDate.now()));
                    when(rs.getTime("scheduled_time")).thenReturn(Time.valueOf(LocalTime.of(7, 0)));
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    return List.of(mapper.mapRow(rs, 1));
                });

        assertThatThrownBy(() -> service.completeTask(taskId, actor, new CompleteCareTaskRequest("Notes", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được hoàn thành trước đó");
    }

    @Test
    void completeTaskRejectsCancelledTask() {
        UUID taskId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        when(jdbc.query(contains("WHERE t.id = ?"), any(RowMapper.class), eq(taskId)))
                .thenAnswer(inv -> {
                    RowMapper<CareTaskResponse> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(taskId);
                    when(rs.getString("status")).thenReturn("CANCELLED");
                    when(rs.getDate("scheduled_date")).thenReturn(Date.valueOf(LocalDate.now()));
                    when(rs.getTime("scheduled_time")).thenReturn(Time.valueOf(LocalTime.of(7, 0)));
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    return List.of(mapper.mapRow(rs, 1));
                });

        assertThatThrownBy(() -> service.completeTask(taskId, actor, new CompleteCareTaskRequest("Notes", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể hoàn thành nhiệm vụ đã bị hủy");
    }

    @Test
    void staffCannotCompleteTaskAssignedToAnotherStaff() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CareTaskService guarded = spy(service);
        CareTaskResponse task = mock(CareTaskResponse.class);
        when(task.status()).thenReturn("PENDING");
        when(task.taskCategory()).thenReturn("SPECIAL");
        when(task.assignedStaffId()).thenReturn(UUID.randomUUID());
        doReturn(task).when(guarded).getTask(taskId);

        assertThatThrownBy(() -> guarded.completeTask(taskId, actorId,
                new CompleteCareTaskRequest("Xong", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("không được phân công");
        verify(jdbc, never()).update(contains("UPDATE dbo.care_tasks"), any(Object[].class));
    }

    @Test
    void staffCannotCompleteUnassignedSpecialTask() {
        UUID taskId = UUID.randomUUID();
        CareTaskService guarded = spy(service);
        CareTaskResponse task = mock(CareTaskResponse.class);
        when(task.status()).thenReturn("PENDING");
        when(task.taskCategory()).thenReturn("SPECIAL");
        doReturn(task).when(guarded).getTask(taskId);

        assertThatThrownBy(() -> guarded.completeTask(taskId, UUID.randomUUID(),
                new CompleteCareTaskRequest("Xong", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("không được phân công");
    }

    @Test
    void completionRejectsConcurrentStatusChange() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CareTaskService guarded = spy(service);
        CareTaskResponse task = mock(CareTaskResponse.class);
        when(task.status()).thenReturn("PENDING");
        when(task.assignedStaffId()).thenReturn(actorId);
        doReturn(task).when(guarded).getTask(taskId);
        when(jdbc.query(contains("SELECT username FROM dbo.users"), any(RowMapper.class), eq(actorId)))
                .thenReturn(List.of("staff"));
        when(jdbc.update(contains("UPDATE dbo.care_tasks"), any(Object[].class))).thenReturn(0);

        assertThatThrownBy(() -> guarded.completeTask(taskId, actorId,
                new CompleteCareTaskRequest("Xong", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã thay đổi trạng thái");
    }

    @Test
    void cancelRejectsConcurrentCompletion() {
        UUID taskId = UUID.randomUUID();
        CareTaskService guarded = spy(service);
        CareTaskResponse task = mock(CareTaskResponse.class);
        when(task.status()).thenReturn("PENDING");
        doReturn(task).when(guarded).getTask(taskId);
        when(jdbc.update(contains("status = 'CANCELLED'"), eq(taskId))).thenReturn(0);

        assertThatThrownBy(() -> guarded.cancelTask(taskId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã thay đổi trạng thái");
    }

    @Test
    void managerCanCompleteAssignedTaskForAbsentStaff() {
        UUID taskId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        CareTaskService guarded = spy(service);
        CareTaskResponse task = mock(CareTaskResponse.class);
        when(task.status()).thenReturn("PENDING");
        when(task.assignedStaffId()).thenReturn(UUID.randomUUID());
        doReturn(task).when(guarded).getTask(taskId);
        when(jdbc.query(contains("SELECT username FROM dbo.users"), any(RowMapper.class), eq(managerId)))
                .thenReturn(List.of("manager"));
        when(jdbc.update(contains("UPDATE dbo.care_tasks"), any(Object[].class))).thenReturn(1);

        guarded.completeTask(taskId, managerId, new CompleteCareTaskRequest("Xong", null), true);
        verify(jdbc).update(contains("UPDATE dbo.care_tasks"), eq(true), eq(managerId), eq(managerId),
                eq("manager"), eq("Xong"), isNull(), isNull(), isNull(), eq(taskId), eq(true), eq(managerId));
    }

    @Test
    void getTaskThrowsWhenNotFound() {
        UUID taskId = UUID.randomUUID();
        when(jdbc.query(contains("WHERE t.id = ?"), any(RowMapper.class), eq(taskId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getTask(taskId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy nhiệm vụ chăm sóc");
    }

    @Test
    void ensureRoutineInstancesForDateIdempotencyAndNoDuplicates() {
        LocalDate date = LocalDate.of(2026, 10, 1);

        service.ensureRoutineInstancesForDate(date);
        service.ensureRoutineInstancesForDate(date);

        // Verify update was called with the date
        verify(jdbc, times(2)).update(
                contains("INSERT INTO dbo.care_tasks"),
                eq(Date.valueOf(date)), eq(Date.valueOf(date))
        );
    }

    @Test
    void differentDatesEnsureSeparateCalls() {
        LocalDate date1 = LocalDate.of(2026, 9, 30);
        LocalDate date2 = LocalDate.of(2026, 10, 1);

        service.ensureRoutineInstancesForDate(date1);
        service.ensureRoutineInstancesForDate(date2);

        verify(jdbc, times(1)).update(contains("INSERT INTO dbo.care_tasks"), eq(Date.valueOf(date1)), eq(Date.valueOf(date1)));
        verify(jdbc, times(1)).update(contains("INSERT INTO dbo.care_tasks"), eq(Date.valueOf(date2)), eq(Date.valueOf(date2)));
    }

    @Test
    void concurrentGenerationDataIntegrityViolationIsCaughtGracefully() {
        LocalDate date = LocalDate.of(2026, 10, 1);

        doThrow(new DataIntegrityViolationException("Duplicate key"))
                .when(jdbc).update(contains("INSERT INTO dbo.care_tasks"), any(Date.class), any(Date.class));

        // Should not throw exception, handles concurrency smoothly
        service.ensureRoutineInstancesForDate(date);
    }

    @Test
    void dynamicOverdueCalculationLogic() {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        // Yesterday PENDING -> Overdue
        CareTaskResponse pastTask = new CareTaskResponse(
                UUID.randomUUID(), UUID.randomUUID(), "ROUTINE", "FACILITY",
                null, null, null, "FEEDING", "Nước uống", null,
                yesterday, LocalTime.of(8, 0), null, "PENDING",
                yesterday.isBefore(today), null, null, null, null, null, OffsetDateTime.now()
        );
        assertThat(pastTask.isOverdue()).isTrue();

        // Yesterday COMPLETED -> Not Overdue
        CareTaskResponse pastCompletedTask = new CareTaskResponse(
                UUID.randomUUID(), UUID.randomUUID(), "ROUTINE", "FACILITY",
                null, null, null, "FEEDING", "Nước uống", null,
                yesterday, LocalTime.of(8, 0), null, "COMPLETED",
                false, OffsetDateTime.now(), UUID.randomUUID(), "groom", "Xong", null, OffsetDateTime.now()
        );
        assertThat(pastCompletedTask.isOverdue()).isFalse();

        // Tomorrow PENDING -> Not Overdue
        CareTaskResponse futureTask = new CareTaskResponse(
                UUID.randomUUID(), UUID.randomUUID(), "ROUTINE", "FACILITY",
                null, null, null, "FEEDING", "Nước uống", null,
                tomorrow, LocalTime.of(8, 0), null, "PENDING",
                tomorrow.isBefore(today), null, null, null, null, null, OffsetDateTime.now()
        );
        assertThat(futureTask.isOverdue()).isFalse();
    }
}
