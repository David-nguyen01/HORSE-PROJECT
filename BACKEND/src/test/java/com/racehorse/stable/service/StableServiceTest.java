package com.racehorse.stable.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StableServiceTest {

    private JdbcTemplate jdbc;
    private HorseAccessService horseAccess;
    private ObjectMapper json;
    private StableService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        horseAccess = mock(HorseAccessService.class);
        json = new ObjectMapper();
        service = new StableService(jdbc, horseAccess, json);
    }

    @Test
    void createStableWithStalls() {
        UUID actor = UUID.randomUUID();
        when(jdbc.query(contains("where id=?"), any(RowMapper.class), any()))
                .thenAnswer(inv -> List.of(new StableService.StableView(
                        inv.getArgument(2), "Barn Alpha", "North Wing", true, List.of())));

        StableService.StableView stable = service.create(actor, "Barn Alpha", "North Wing", List.of("S-01", "S-02"));

        assertThat(stable.name()).isEqualTo("Barn Alpha");
        verify(jdbc).update(contains("insert into stables"), any(), eq("Barn Alpha"), eq("North Wing"));
        verify(jdbc, times(2)).update(contains("insert into module_records"), any(), eq(actor), anyString());
    }

    @Test
    void addDailyRoutineValidatesTimeAndPersists() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        StableService.DailyRoutineRequest valid = new StableService.DailyRoutineRequest(
                "TURNOUT", LocalTime.of(7, 0), LocalTime.of(8, 30), "Morning grass turnout");

        StableService.DailyRoutineView view = service.addDailyRoutine(horseId, actor, valid);

        assertThat(view.horseId()).isEqualTo(horseId);
        assertThat(view.activityType()).isEqualTo("TURNOUT");
        assertThat(view.startTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(view.endTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(view.notes()).isEqualTo("Morning grass turnout");
        assertThat(view.active()).isTrue();

        verify(horseAccess).requireActive(horseId);
        verify(jdbc).update(contains("insert into module_records"), any(), eq(horseId), eq(actor), anyString());
    }

    @Test
    void addDailyRoutineRejectsInvalidTime() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        StableService.DailyRoutineRequest invalid = new StableService.DailyRoutineRequest(
                "TURNOUT", LocalTime.of(9, 0), LocalTime.of(8, 30), "Invalid time");

        assertThatThrownBy(() -> service.addDailyRoutine(horseId, actor, invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giờ bắt đầu phải trước giờ kết thúc");
    }

    @Test
    void listDailyRoutinesReturnsChronologicalItems() {
        UUID horseId = UUID.randomUUID();

        when(jdbc.query(contains("order by JSON_VALUE(data_json,'$.startTime') asc"), any(RowMapper.class), eq(horseId)))
                .thenReturn(List.of(
                        new StableService.DailyRoutineView(UUID.randomUUID(), horseId, "FEEDING", LocalTime.of(6, 0), LocalTime.of(6, 30), "Breakfast", true),
                        new StableService.DailyRoutineView(UUID.randomUUID(), horseId, "TURNOUT", LocalTime.of(7, 0), LocalTime.of(8, 30), "Paddock", true),
                        new StableService.DailyRoutineView(UUID.randomUUID(), horseId, "GROOMING", LocalTime.of(14, 0), LocalTime.of(15, 0), "Brush & Wash", true)
                ));

        List<StableService.DailyRoutineView> list = service.listDailyRoutines(horseId);

        assertThat(list).hasSize(3);
        assertThat(list.get(0).activityType()).isEqualTo("FEEDING");
        assertThat(list.get(1).activityType()).isEqualTo("TURNOUT");
        assertThat(list.get(2).activityType()).isEqualTo("GROOMING");
    }

    @Test
    void deleteDailyRoutineSetsStatusInactive() {
        UUID routineId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();

        when(jdbc.query(contains("where id=? and module_code='STABLE_CARE' and record_type='HORSE_DAILY_ROUTINE'"), any(RowMapper.class), eq(routineId)))
                .thenReturn(List.of(new StableService.DailyRoutineView(
                        routineId, horseId, "REST", LocalTime.of(12, 0), LocalTime.of(13, 30), "Rest", true)));

        service.deleteDailyRoutine(routineId);

        verify(jdbc).update(contains("update module_records set status='INACTIVE'"), eq(routineId));
    }
}
