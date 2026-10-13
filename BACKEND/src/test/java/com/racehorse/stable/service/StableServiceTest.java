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

        when(jdbc.queryForObject(contains("from training_locks"), eq(Integer.class), eq(horseId))).thenReturn(0);

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
    void addDailyRoutineRejectsTurnoutWhenHorseInjured() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Horse horse = mock(Horse.class);
        when(horse.getHealthStatus()).thenReturn("INJURED");
        when(horseAccess.requireActive(horseId)).thenReturn(horse);

        StableService.DailyRoutineRequest request = new StableService.DailyRoutineRequest(
                "TURNOUT", LocalTime.of(7, 0), LocalTime.of(8, 30), "Grass turnout");

        assertThatThrownBy(() -> service.addDailyRoutine(horseId, actor, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được phép xếp lịch thả bãi (TURNOUT)");
    }

    @Test
    void addDailyRoutineRejectsTurnoutWhenHorseTrainingLocked() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Horse horse = mock(Horse.class);
        when(horse.getHealthStatus()).thenReturn("FIT");
        when(horseAccess.requireActive(horseId)).thenReturn(horse);
        when(jdbc.queryForObject(contains("from training_locks"), eq(Integer.class), eq(horseId))).thenReturn(1);

        StableService.DailyRoutineRequest request = new StableService.DailyRoutineRequest(
                "TURNOUT", LocalTime.of(7, 0), LocalTime.of(8, 30), "Grass turnout");

        assertThatThrownBy(() -> service.addDailyRoutine(horseId, actor, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được phép xếp lịch thả bãi (TURNOUT)");
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
<<<<<<< Updated upstream
=======

    @Test
    void assignLocksResourcesAndSucceeds() {
        UUID stableId = UUID.randomUUID();
        UUID stallId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        StableService.StallView stall = new StableService.StallView(stallId, "S-01", true, null);
        StableService.StableView stable = new StableService.StableView(stableId, "Barn Alpha", "North Wing", true, List.of(stall));

        when(jdbc.query(contains("from stables where id=?"), any(RowMapper.class), eq(stableId)))
                .thenReturn(List.of(stable));

        Horse horse = mock(Horse.class);
        when(horse.getName()).thenReturn("Thunder");
        when(horse.getCode()).thenReturn("H-01");
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);
        when(horse.getIntakeStatus()).thenReturn("PENDING_STALL");
        when(horse.getDiagnosingVet()).thenReturn(mock(com.racehorse.auth.entity.UserAccount.class));

        when(jdbc.queryForObject(contains("select id from module_records with (UPDLOCK, HOLDLOCK)"), eq(UUID.class), eq(stallId)))
                .thenReturn(stallId);
        when(jdbc.queryForObject(contains("select id from stables with (UPDLOCK, HOLDLOCK)"), eq(UUID.class), eq(stableId)))
                .thenReturn(stableId);
        when(jdbc.queryForObject(contains("select id from horses with (UPDLOCK, HOLDLOCK)"), eq(UUID.class), eq(horseId)))
                .thenReturn(horseId);

        when(jdbc.queryForObject(contains("count(distinct horse_id) from module_records with (UPDLOCK, HOLDLOCK)"), eq(Integer.class), eq(stableId.toString()), eq(horseId)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("count(*) from module_records with (UPDLOCK, HOLDLOCK)"), eq(Integer.class), eq(stallId.toString()), eq(horseId)))
                .thenReturn(0);

        StableService.AssignmentView result = service.assign(stableId, stallId, horseId, actor);

        assertThat(result.stableId()).isEqualTo(stableId);
        assertThat(result.stallId()).isEqualTo(stallId);
        assertThat(result.horseId()).isEqualTo(horseId);
        assertThat(result.horseName()).isEqualTo("Thunder");
        assertThat(result.active()).isTrue();

        verify(jdbc).queryForObject(contains("select id from module_records with (UPDLOCK, HOLDLOCK)"), eq(UUID.class), eq(stallId));
        verify(jdbc).queryForObject(contains("select id from stables with (UPDLOCK, HOLDLOCK)"), eq(UUID.class), eq(stableId));
        verify(jdbc).queryForObject(contains("select id from horses with (UPDLOCK, HOLDLOCK)"), eq(UUID.class), eq(horseId));
        verify(jdbc).update(contains("insert into module_records"), any(), eq(horseId), eq(actor), any(), anyString());
        verify(jdbc).update(contains("update horses set stable_location=?"), eq("Barn Alpha / S-01"), eq(horseId));
    }

    @Test
    void assignRejectsWhenStallAlreadyOccupied() {
        UUID stableId = UUID.randomUUID();
        UUID stallId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        StableService.StallView stall = new StableService.StallView(stallId, "S-01", true, null);
        StableService.StableView stable = new StableService.StableView(stableId, "Barn Alpha", "North Wing", true, List.of(stall));

        when(jdbc.query(contains("from stables where id=?"), any(RowMapper.class), eq(stableId)))
                .thenReturn(List.of(stable));

        Horse horse = mock(Horse.class);
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);
        when(horse.getIntakeStatus()).thenReturn("PENDING_STALL");
        when(horse.getDiagnosingVet()).thenReturn(mock(com.racehorse.auth.entity.UserAccount.class));

        when(jdbc.queryForObject(contains("count(distinct horse_id) from module_records with (UPDLOCK, HOLDLOCK)"), eq(Integer.class), eq(stableId.toString()), eq(horseId)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("count(*) from module_records with (UPDLOCK, HOLDLOCK)"), eq(Integer.class), eq(stallId.toString()), eq(horseId)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.assign(stableId, stallId, horseId, actor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ô chuồng đã có ngựa");
    }

    @Test
    void assignRejectsWhenStableExceedsCapacity() {
        UUID stableId = UUID.randomUUID();
        UUID stallId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        StableService.StallView stall = new StableService.StallView(stallId, "S-01", true, null);
        StableService.StableView stable = new StableService.StableView(stableId, "Barn Alpha", "North Wing", true, List.of(stall));

        when(jdbc.query(contains("from stables where id=?"), any(RowMapper.class), eq(stableId)))
                .thenReturn(List.of(stable));

        Horse horse = mock(Horse.class);
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);
        when(horse.getIntakeStatus()).thenReturn("PENDING_STALL");
        when(horse.getDiagnosingVet()).thenReturn(mock(com.racehorse.auth.entity.UserAccount.class));

        when(jdbc.queryForObject(contains("count(distinct horse_id) from module_records with (UPDLOCK, HOLDLOCK)"), eq(Integer.class), eq(stableId.toString()), eq(horseId)))
                .thenReturn(4);

        assertThatThrownBy(() -> service.assign(stableId, stallId, horseId, actor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Khu chuồng này đã đạt sức chứa tối đa 4 con ngựa");
    }

    @Test
    void assignTranslatesDatabaseTriggerConstraintViolation() {
        UUID stableId = UUID.randomUUID();
        UUID stallId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        StableService.StallView stall = new StableService.StallView(stallId, "S-01", true, null);
        StableService.StableView stable = new StableService.StableView(stableId, "Barn Alpha", "North Wing", true, List.of(stall));

        when(jdbc.query(contains("from stables where id=?"), any(RowMapper.class), eq(stableId)))
                .thenReturn(List.of(stable));

        Horse horse = mock(Horse.class);
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);
        when(horse.getIntakeStatus()).thenReturn("PENDING_STALL");
        when(horse.getDiagnosingVet()).thenReturn(mock(com.racehorse.auth.entity.UserAccount.class));

        when(jdbc.queryForObject(contains("count(distinct horse_id) from module_records with (UPDLOCK, HOLDLOCK)"), eq(Integer.class), eq(stableId.toString()), eq(horseId)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("count(*) from module_records with (UPDLOCK, HOLDLOCK)"), eq(Integer.class), eq(stallId.toString()), eq(horseId)))
                .thenReturn(0);

        when(jdbc.update(contains("insert into module_records"), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("52001: Ô chuồng đã có phân bổ hoạt động, không thể phân bổ trùng."));

        assertThatThrownBy(() -> service.assign(stableId, stallId, horseId, actor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ô chuồng đã có phân bổ hoạt động, không thể phân bổ trùng");
    }

    @Test
    void createDefaultDailyRoutineIfAbsentCreatesHealthyRoutineWhenFit() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        // 0 existing routines
        when(jdbc.queryForObject(contains("record_type='HORSE_DAILY_ROUTINE' and horse_id=?"), eq(Integer.class), eq(horseId)))
                .thenReturn(0);

        Horse horse = mock(Horse.class);
        when(horse.getHealthStatus()).thenReturn("FIT");
        when(horseAccess.requireActive(horseId)).thenReturn(horse);

        when(jdbc.queryForObject(contains("from training_locks where horse_id=?"), eq(Integer.class), eq(horseId)))
                .thenReturn(0);

        // Template found in DB
        String healthyTemplateJson = """
                {"templateCode":"TEMPLATE_DEFAULT_HEALTHY","activities":[{"activityType":"GROOMING","startTime":"06:00","endTime":"07:00","notes":"Chải lông"},{"activityType":"TURNOUT","startTime":"07:30","endTime":"08:30","notes":"Thả cỏ"}]}
                """;
        when(jdbc.query(contains("record_type='DAILY_ROUTINE_TEMPLATE'"), any(RowMapper.class), eq("TEMPLATE_DEFAULT_HEALTHY")))
                .thenReturn(List.of(healthyTemplateJson));

        service.createDefaultDailyRoutineIfAbsent(horseId, horse, actor);

        // 2 activities inserted into module_records
        verify(jdbc, times(2)).update(contains("insert into module_records"), any(), eq(horseId), eq(actor), anyString());
    }

    @Test
    void createDefaultDailyRoutineIfAbsentCreatesStallRestWhenInjuredOrLocked() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        // 0 existing routines
        when(jdbc.queryForObject(contains("record_type='HORSE_DAILY_ROUTINE' and horse_id=?"), eq(Integer.class), eq(horseId)))
                .thenReturn(0);

        Horse horse = mock(Horse.class);
        when(horse.getHealthStatus()).thenReturn("INJURED");
        when(horseAccess.requireActive(horseId)).thenReturn(horse);

        when(jdbc.queryForObject(contains("from training_locks where horse_id=?"), eq(Integer.class), eq(horseId)))
                .thenReturn(1);

        String restTemplateJson = """
                {"templateCode":"TEMPLATE_STALL_REST","activities":[{"activityType":"STALL_REST","startTime":"07:00","endTime":"08:00","notes":"Nghỉ chuồng"},{"activityType":"ICE_BOOTS","startTime":"09:30","endTime":"10:30","notes":"Chườm đá"}]}
                """;
        when(jdbc.query(contains("record_type='DAILY_ROUTINE_TEMPLATE'"), any(RowMapper.class), eq("TEMPLATE_STALL_REST")))
                .thenReturn(List.of(restTemplateJson));

        service.createDefaultDailyRoutineIfAbsent(horseId, horse, actor);

        // 2 activities inserted, generated from TEMPLATE_STALL_REST
        verify(jdbc, times(2)).update(contains("insert into module_records"), any(), eq(horseId), eq(actor), anyString());
    }

    @Test
    void createDefaultDailyRoutineIfAbsentIsIdempotentAndDoesNotOverwriteExistingRoutines() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        // Already has 4 active routines
        when(jdbc.queryForObject(contains("record_type='HORSE_DAILY_ROUTINE' and horse_id=?"), eq(Integer.class), eq(horseId)))
                .thenReturn(4);

        service.createDefaultDailyRoutineIfAbsent(horseId, actor);

        // No new inserts!
        verify(jdbc, never()).update(contains("insert into module_records(id,module_code,record_type"), any(), eq(horseId), any(), anyString());
    }

    @Test
    void transferHorseRejectsTransferWhenDiagnosingVetMissing() {
        UUID horseId = UUID.randomUUID();
        UUID targetStableId = UUID.randomUUID();
        UUID targetStallId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        when(jdbc.query(contains("where id=?"), any(RowMapper.class), eq(targetStableId)))
                .thenReturn(List.of(new StableService.StableView(targetStableId, "Stable A", "Loc", true,
                        List.of(new StableService.StallView(targetStallId, "A-01", true, null)))));

        Horse horse = mock(Horse.class);
        when(horse.getDiagnosingVet()).thenReturn(null);
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);

        assertThatThrownBy(() -> service.transferHorse(horseId, targetStableId, targetStallId, actor, "Chuyển ô"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bác sĩ phải hoàn tất khám");
    }

    @Test
    void transferHorseRejectsSameStallTransfer() {
        UUID horseId = UUID.randomUUID();
        UUID targetStableId = UUID.randomUUID();
        UUID targetStallId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        when(jdbc.query(contains("where id=?"), any(RowMapper.class), eq(targetStableId)))
                .thenReturn(List.of(new StableService.StableView(targetStableId, "Stable A", "Loc", true,
                        List.of(new StableService.StallView(targetStallId, "A-01", true, null)))));

        Horse horse = mock(Horse.class);
        when(horse.getDiagnosingVet()).thenReturn(mock(com.racehorse.auth.entity.UserAccount.class));
        when(horse.getIntakeStatus()).thenReturn("STABLED");
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);

        // Current assignment is in the same stall: targetStallId
        when(jdbc.query(contains("where m.horse_id=? and m.module_code='STABLE_CARE' and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE'"),
                any(RowMapper.class), eq(horseId)))
                .thenReturn(List.of(new StableService.AssignmentView(UUID.randomUUID(), targetStableId, "Stable A",
                        targetStallId, "A-01", horseId, "Thunder", "TH-01", actor, null, null, true)));

        assertThatThrownBy(() -> service.transferHorse(horseId, targetStableId, targetStallId, actor, "Chuyển ô"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ô chuồng đích phải khác ô chuồng hiện tại");
    }

    @Test
    void transferHorseRejectsWhenTargetStableExceedsCapacity() {
        UUID horseId = UUID.randomUUID();
        UUID currentStallId = UUID.randomUUID();
        UUID targetStableId = UUID.randomUUID();
        UUID targetStallId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        when(jdbc.query(contains("where id=?"), any(RowMapper.class), eq(targetStableId)))
                .thenReturn(List.of(new StableService.StableView(targetStableId, "Stable B", "Loc", true,
                        List.of(new StableService.StallView(targetStallId, "B-01", true, null)))));

        Horse horse = mock(Horse.class);
        when(horse.getDiagnosingVet()).thenReturn(mock(com.racehorse.auth.entity.UserAccount.class));
        when(horse.getIntakeStatus()).thenReturn("STABLED");
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);

        when(jdbc.query(contains("where m.horse_id=? and m.module_code='STABLE_CARE' and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE'"),
                any(RowMapper.class), eq(horseId)))
                .thenReturn(List.of(new StableService.AssignmentView(UUID.randomUUID(), UUID.randomUUID(), "Stable A",
                        currentStallId, "A-01", horseId, "Thunder", "TH-01", actor, null, null, true)));

        // Target stable already has 4 horses
        when(jdbc.queryForObject(contains("from module_records with (UPDLOCK, HOLDLOCK) where module_code='STABLE_CARE' and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stableId')=? and horse_id<>?"),
                eq(Integer.class), eq(targetStableId.toString()), eq(horseId)))
                .thenReturn(4);

        assertThatThrownBy(() -> service.transferHorse(horseId, targetStableId, targetStallId, actor, "Chuyển ô"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sức chứa tối đa 4 con ngựa");
    }

    @Test
    void transferHorseRejectsWhenQuarantinedHorseMovedToNonQuarantinedStable() {
        UUID horseId = UUID.randomUUID();
        UUID currentStallId = UUID.randomUUID();
        UUID targetStableId = UUID.randomUUID();
        UUID targetStallId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        when(jdbc.query(contains("where id=?"), any(RowMapper.class), eq(targetStableId)))
                .thenReturn(List.of(new StableService.StableView(targetStableId, "Stable B", "Loc", true,
                        List.of(new StableService.StallView(targetStallId, "B-01", true, null)))));

        Horse horse = mock(Horse.class);
        when(horse.getDiagnosingVet()).thenReturn(mock(com.racehorse.auth.entity.UserAccount.class));
        when(horse.getIntakeStatus()).thenReturn("STABLED");
        when(horse.getHealthStatus()).thenReturn("QUARANTINED");
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);

        when(jdbc.query(contains("where m.horse_id=? and m.module_code='STABLE_CARE' and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE'"),
                any(RowMapper.class), eq(horseId)))
                .thenReturn(List.of(new StableService.AssignmentView(UUID.randomUUID(), UUID.randomUUID(), "Stable A",
                        currentStallId, "A-01", horseId, "Thunder", "TH-01", actor, null, null, true)));

        when(jdbc.queryForObject(contains("from module_records with (UPDLOCK, HOLDLOCK) where module_code='STABLE_CARE' and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stableId')=? and horse_id<>?"),
                eq(Integer.class), eq(targetStableId.toString()), eq(horseId)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("from module_records with (UPDLOCK, HOLDLOCK) where module_code='STABLE_CARE' and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stallId')=? and horse_id<>?"),
                eq(Integer.class), eq(targetStallId.toString()), eq(horseId)))
                .thenReturn(0);

        // Destination stable has non-quarantined horses
        when(jdbc.query(contains("select distinct coalesce(h.health_status, 'FIT')"),
                any(RowMapper.class), eq(targetStableId.toString()), eq(horseId)))
                .thenReturn(List.of("FIT"));

        assertThatThrownBy(() -> service.transferHorse(horseId, targetStableId, targetStallId, actor, "Chuyển ô"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("QUARANTINED");
    }

    @Test
    void transferHorseSucceedsAndUpdatesAssignments() {
        UUID horseId = UUID.randomUUID();
        UUID currentStallId = UUID.randomUUID();
        UUID targetStableId = UUID.randomUUID();
        UUID targetStallId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID oldAssignmentId = UUID.randomUUID();

        when(jdbc.query(contains("where id=?"), any(RowMapper.class), eq(targetStableId)))
                .thenReturn(List.of(new StableService.StableView(targetStableId, "Barn Beta", "Loc", true,
                        List.of(new StableService.StallView(targetStallId, "B-02", true, null)))));

        Horse horse = mock(Horse.class);
        when(horse.getName()).thenReturn("Storm");
        when(horse.getCode()).thenReturn("ST-01");
        when(horse.getDiagnosingVet()).thenReturn(mock(com.racehorse.auth.entity.UserAccount.class));
        when(horse.getIntakeStatus()).thenReturn("STABLED");
        when(horse.getHealthStatus()).thenReturn("FIT");
        when(horseAccess.requireServiceEligible(horseId)).thenReturn(horse);

        when(jdbc.query(contains("where m.horse_id=? and m.module_code='STABLE_CARE' and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE'"),
                any(RowMapper.class), eq(horseId)))
                .thenReturn(List.of(new StableService.AssignmentView(oldAssignmentId, UUID.randomUUID(), "Barn Alpha",
                        currentStallId, "A-01", horseId, "Storm", "ST-01", actor, null, null, true)));

        when(jdbc.queryForObject(contains("from module_records with (UPDLOCK, HOLDLOCK) where module_code='STABLE_CARE' and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stableId')=? and horse_id<>?"),
                eq(Integer.class), eq(targetStableId.toString()), eq(horseId)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("from module_records with (UPDLOCK, HOLDLOCK) where module_code='STABLE_CARE' and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stallId')=? and horse_id<>?"),
                eq(Integer.class), eq(targetStallId.toString()), eq(horseId)))
                .thenReturn(0);

        when(jdbc.query(contains("select distinct coalesce(h.health_status, 'FIT')"),
                any(RowMapper.class), eq(targetStableId.toString()), eq(horseId)))
                .thenReturn(List.of("FIT"));

        var result = service.transferHorse(horseId, targetStableId, targetStallId, actor, "Chuyển do bảo trì");

        assertThat(result.horseId()).isEqualTo(horseId);
        assertThat(result.stableName()).isEqualTo("Barn Beta");
        assertThat(result.stallCode()).isEqualTo("B-02");
        assertThat(result.active()).isTrue();

        // Verifies previous assignment ended with reason
        verify(jdbc).update(contains("update module_records set status='ENDED'"), anyString(), eq(actor.toString()),
                eq("TRANSFERRED: Chuyển do bảo trì"), eq(oldAssignmentId));

        // Verifies new assignment inserted
        verify(jdbc).update(contains("insert into module_records"), any(UUID.class), eq(horseId), eq(actor), any(), anyString());

        // Verifies horse location updated
        verify(jdbc).update(contains("update horses set stable_location=?"), eq("Barn Beta / B-02"), eq(horseId));
    }
>>>>>>> Stashed changes
}
