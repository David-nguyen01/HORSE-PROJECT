package com.racehorse.health.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.health.dto.CompletePreventiveCareRequest;
import com.racehorse.health.dto.CreatePreventiveCareRequest;
import com.racehorse.health.dto.DuePreventiveCareResponse;
import com.racehorse.health.dto.PreventiveCareResponse;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreventiveCareServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private HorseAccessService access;
    private PreventiveCareService service;

    @BeforeEach
    void setUp() {
        service = new PreventiveCareService(jdbc, access);
    }

    @Test
    void createRejectsInvalidCareType() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        CreatePreventiveCareRequest req = new CreatePreventiveCareRequest(
                "MASSAGE", "Back massage", LocalDate.now().plusDays(10), 30, 3);

        assertThatThrownBy(() -> service.create(horseId, vetId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("careType không hợp lệ");
    }

    @Test
    void createPersistsValidCareSchedule() {
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        CreatePreventiveCareRequest req = new CreatePreventiveCareRequest(
                "VACCINATION", "Rabies booster", LocalDate.now().plusDays(14), 180, 7);

        PreventiveCareResponse response = service.create(horseId, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.careType()).isEqualTo("VACCINATION");
        assertThat(response.dueDate()).isEqualTo(req.dueDate());
        assertThat(response.recurrenceDays()).isEqualTo(180);
        assertThat(response.reminderLeadDays()).isEqualTo(7);

        verify(access).requireActive(horseId);
        verify(jdbc).update(contains("insert into preventive_care_schedules"),
                any(), eq(horseId), eq("VACCINATION"), eq("Rabies booster"),
                eq(req.dueDate()), eq(180), eq(7), eq(vetId));
    }

    @Test
    void completeSchedulesNextRecurringTask() {
        UUID id = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        LocalDate due = LocalDate.now();

        Map<String, Object> scheduleMap = Map.of(
                "id", id,
                "horse_id", horseId,
                "care_type", "DEWORMING",
                "details", "Broad spectrum paste",
                "due_date", due,
                "recurrence_days", 90,
                "reminder_lead_days", 5
        );

        when(jdbc.queryForList(contains("select * from preventive_care_schedules with (UPDLOCK,HOLDLOCK) where id=?"), eq(id)))
                .thenReturn(List.of(scheduleMap));
        when(jdbc.update(contains("where id=? and completed_at is null and cancelled_at is null"),
                eq(vetId), any(OffsetDateTime.class), eq("Completed without issue"), eq(id))).thenReturn(1);

        CompletePreventiveCareRequest req = new CompletePreventiveCareRequest(OffsetDateTime.now(), "Completed without issue");

        PreventiveCareResponse response = service.complete(id, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.completed()).isTrue();
        assertThat(response.nextScheduleId()).isNotNull();

        // Verify next schedule was inserted
        verify(jdbc).update(contains("insert into preventive_care_schedules"),
                eq(response.nextScheduleId()), eq(horseId), eq("DEWORMING"),
                eq("Broad spectrum paste"), eq(90), eq(due), eq(90), eq(5), eq(vetId));

        // Verify current schedule was updated with completion details
        verify(jdbc).update(contains("update preventive_care_schedules set completed_by_user_id=?"),
                eq(vetId), eq(req.performedAt()), eq("Completed without issue"), eq(id));
        verify(jdbc).update(contains("set next_schedule_id=?"), eq(response.nextScheduleId()), eq(id));
    }

    @Test
    void completingAlreadyCompletedScheduleDoesNotCreateAnotherSuccessor() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(contains("with (UPDLOCK,HOLDLOCK)"), eq(id)))
                .thenReturn(List.of(Map.of("completed_at", OffsetDateTime.now())));

        assertThatThrownBy(() -> service.complete(id, UUID.randomUUID(),
                new CompletePreventiveCareRequest(OffsetDateTime.now(), "done")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được hoàn thành");
        verify(jdbc, never()).update(contains("insert into preventive_care_schedules"), any(Object[].class));
    }

    @Test
    void failedConditionalCompletionDoesNotCreateRecurringSchedule() {
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(jdbc.queryForList(contains("with (UPDLOCK,HOLDLOCK)"), eq(id)))
                .thenReturn(List.of(Map.of("recurrence_days", 30)));
        when(jdbc.update(contains("where id=? and completed_at is null and cancelled_at is null"),
                eq(actor), any(OffsetDateTime.class), anyString(), eq(id))).thenReturn(0);

        assertThatThrownBy(() -> service.complete(id, actor,
                new CompletePreventiveCareRequest(OffsetDateTime.now(), "done")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xử lý");
        verify(jdbc, never()).update(contains("insert into preventive_care_schedules"), any(Object[].class));
    }

    @Test
    void listDueQueriesDueAndOverdueItems() {
        LocalDate asOf = LocalDate.of(2026, 9, 20);
        UUID schedId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();

        DuePreventiveCareResponse mockItem = new DuePreventiveCareResponse(
                schedId, horseId, "H-001", "Thunder", "FARRIER", "Shoeing check",
                LocalDate.of(2026, 9, 15), 45, 5, true, -5);

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(mockItem));

        List<DuePreventiveCareResponse> list = service.listDue(asOf, "FARRIER", true);

        assertThat(list).hasSize(1);
        DuePreventiveCareResponse item = list.getFirst();
        assertThat(item.isOverdue()).isTrue();
        assertThat(item.daysUntilDue()).isEqualTo(-5);
        assertThat(item.horseCode()).isEqualTo("H-001");
    }
}
