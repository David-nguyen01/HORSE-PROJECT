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

        when(jdbc.queryForList(contains("select * from preventive_care_schedules where id=? and cancelled_at is null"), eq(id)))
                .thenReturn(List.of(scheduleMap));

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
                eq(vetId), eq(req.performedAt()), eq("Completed without issue"), eq(response.nextScheduleId()), eq(id));
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
