package com.racehorse.stable.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class StableService {
    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccess;
    private final ObjectMapper json;

    public StableService(JdbcTemplate jdbc, HorseAccessService horseAccess, ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseAccess = horseAccess;
        this.json = json;
    }

    @Transactional
    public StableView create(UUID actor, String name, String location, List<String> stallCodes) {
        String normalizedName = required(name, "Tên chuồng không được để trống");
        UUID stableId = UUID.randomUUID();
        try {
            jdbc.update("insert into stables(id,name,location,is_active) values(?,?,?,1)",
                    stableId, normalizedName, trimToNull(location));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Tên chuồng đã tồn tại");
        }
        if (stallCodes != null) {
            stallCodes.stream().map(this::normalizeCode).distinct().forEach(code -> addStallInternal(stableId, actor, code));
        }
        return get(stableId);
    }

    @Transactional(readOnly = true)
    public List<StableView> list(boolean includeInactive) {
        return jdbc.query("select id,name,location,is_active from stables "
                        + (includeInactive ? "" : "where is_active=1 ") + "order by name",
                (rs, row) -> buildStable(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("location"), rs.getBoolean("is_active")));
    }

    @Transactional(readOnly = true)
    public StableView get(UUID stableId) {
        List<StableView> rows = jdbc.query("select id,name,location,is_active from stables where id=?",
                (rs, row) -> buildStable(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("location"), rs.getBoolean("is_active")), stableId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy chuồng: " + stableId);
        return rows.getFirst();
    }

    @Transactional
    public StableView update(UUID stableId, String name, String location, Boolean active) {
        get(stableId);
        String normalizedName = required(name, "Tên chuồng không được để trống");
        try {
            jdbc.update("update stables set name=?,location=?,is_active=? where id=?",
                    normalizedName, trimToNull(location), active == null || active, stableId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Tên chuồng đã tồn tại");
        }
        return get(stableId);
    }

    @Transactional
    public StableView addStall(UUID stableId, UUID actor, String stallCode) {
        requireActiveStable(stableId);
        addStallInternal(stableId, actor, normalizeCode(stallCode));
        return get(stableId);
    }

    @Transactional
    public void deactivateStall(UUID stableId, UUID stallId) {
        requireStall(stableId, stallId);
        Integer occupied = jdbc.queryForObject("select count(*) from module_records where module_code='STABLE_CARE' "
                + "and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stallId')=?",
                Integer.class, stallId.toString());
        if (occupied != null && occupied > 0) throw new BusinessException("Không thể ngừng ô chuồng đang có ngựa");
        jdbc.update("update module_records set status='INACTIVE',data_json=JSON_MODIFY(data_json,'$.isActive',cast(0 as bit)),updated_at=SYSDATETIMEOFFSET() where id=?",
                stallId);
    }

    @Transactional
    public AssignmentView assign(UUID stableId, UUID stallId, UUID horseId, UUID actor) {
        StableView stable = requireActiveStable(stableId);
        StallView stall = requireStall(stableId, stallId);
        if (!stall.active()) throw new BusinessException("Ô chuồng đang ngừng hoạt động");
        if(stall.currentAssignment()!=null&&stall.currentAssignment().horseId().equals(horseId))
            throw new BusinessException("Ngựa đã được phân vào ô chuồng này");
        horseAccess.requireActive(horseId);
        Integer occupied = jdbc.queryForObject("select count(*) from module_records where module_code='STABLE_CARE' "
                + "and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stallId')=? and horse_id<>?",
                Integer.class, stallId.toString(), horseId);
        if (occupied != null && occupied > 0) throw new BusinessException("Ô chuồng đã có ngựa");
        OffsetDateTime now = OffsetDateTime.now();
        endCurrentHorseAssignment(horseId, now);
        UUID assignmentId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stableId", stableId.toString());
        data.put("stallId", stallId.toString());
        data.put("stallCode", stall.code());
        data.put("assignedFrom", now.toString());
        data.put("assignedUntil", null);
        jdbc.update("insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,event_date,data_json) "
                        + "values(?,'STABLE_CARE','STALL_ASSIGNMENT','api',?,?,'ACTIVE',cast(? as date),?)",
                assignmentId, horseId, actor, now, toJson(data));
        jdbc.update("update horses set stable_location=?,updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                stable.name() + " / " + stall.code(), horseId);
        HorseSummary horse = getHorseSummary(horseId);
        return new AssignmentView(assignmentId, stableId, stable.name(), stallId, stall.code(), horseId,
                horse.code(), horse.name(), horse.healthStatus(), actor, now, null, true);
    }

    @Transactional
    public AssignmentView unassign(UUID assignmentId, UUID actor) {
        AssignmentView current = requireAssignment(assignmentId);
        if (!current.active()) throw new BusinessException("Phân bổ chuồng đã kết thúc");
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("update module_records set status='ENDED',data_json=JSON_MODIFY(JSON_MODIFY(data_json,'$.assignedUntil',?),'$.endedByUserId',?),updated_at=SYSDATETIMEOFFSET() where id=?",
                now.toString(),actor.toString(),assignmentId);
        jdbc.update("update horses set stable_location='',updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                current.horseId());
        return new AssignmentView(current.id(), current.stableId(), current.stableName(), current.stallId(),
                current.stallCode(), current.horseId(), current.horseCode(), current.horseName(), current.horseHealthStatus(),
                current.assignedBy(), current.assignedFrom(), now, false);
    }

    // Daily Routine (FR13)
    @Transactional(readOnly = true)
    public List<DailyRoutineView> listDailyRoutines(UUID horseId) {
        horseAccess.requireActive(horseId);
        return jdbc.query("select id, horse_id, status, data_json from module_records "
                        + "where module_code='STABLE_CARE' and record_type='HORSE_DAILY_ROUTINE' and horse_id=? and status='ACTIVE' "
                        + "order by JSON_VALUE(data_json,'$.startTime') asc",
                (rs, row) -> {
                    JsonNode node = read(rs.getString("data_json"));
                    return new DailyRoutineView(
                            rs.getObject("id", UUID.class),
                            rs.getObject("horse_id", UUID.class),
                            node.path("activityType").asText(),
                            LocalTime.parse(node.path("startTime").asText()),
                            LocalTime.parse(node.path("endTime").asText()),
                            node.path("notes").asText(null),
                            "ACTIVE".equals(rs.getString("status"))
                    );
                }, horseId);
    }

    @Transactional
    public DailyRoutineView addDailyRoutine(UUID horseId, UUID actor, DailyRoutineRequest request) {
        horseAccess.requireActive(horseId);
        validateRoutine(request);
        UUID id = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activityType", request.activityType().trim().toUpperCase(Locale.ROOT));
        data.put("startTime", request.startTime().toString());
        data.put("endTime", request.endTime().toString());
        data.put("notes", trimToNull(request.notes()));
        data.put("isActive", true);

        jdbc.update("insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,data_json) "
                        + "values(?,'STABLE_CARE','HORSE_DAILY_ROUTINE','api',?,?,'ACTIVE',?)",
                id, horseId, actor, toJson(data));
        return new DailyRoutineView(id, horseId, request.activityType().trim().toUpperCase(Locale.ROOT),
                request.startTime(), request.endTime(), trimToNull(request.notes()), true);
    }

    @Transactional
    public DailyRoutineView updateDailyRoutine(UUID routineId, UUID actor, DailyRoutineRequest request) {
        DailyRoutineView existing = getDailyRoutine(routineId);
        validateRoutine(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activityType", request.activityType().trim().toUpperCase(Locale.ROOT));
        data.put("startTime", request.startTime().toString());
        data.put("endTime", request.endTime().toString());
        data.put("notes", trimToNull(request.notes()));
        data.put("isActive", true);

        jdbc.update("update module_records set data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                toJson(data), routineId);
        return new DailyRoutineView(routineId, existing.horseId(), request.activityType().trim().toUpperCase(Locale.ROOT),
                request.startTime(), request.endTime(), trimToNull(request.notes()), true);
    }

    @Transactional
    public void deleteDailyRoutine(UUID routineId) {
        getDailyRoutine(routineId);
        jdbc.update("update module_records set status='INACTIVE', updated_at=SYSDATETIMEOFFSET() where id=?", routineId);
    }

    @Transactional(readOnly = true)
    public DailyRoutineView getDailyRoutine(UUID routineId) {
        List<DailyRoutineView> rows = jdbc.query("select id, horse_id, status, data_json from module_records "
                        + "where id=? and module_code='STABLE_CARE' and record_type='HORSE_DAILY_ROUTINE'",
                (rs, row) -> {
                    JsonNode node = read(rs.getString("data_json"));
                    return new DailyRoutineView(
                            rs.getObject("id", UUID.class),
                            rs.getObject("horse_id", UUID.class),
                            node.path("activityType").asText(),
                            LocalTime.parse(node.path("startTime").asText()),
                            LocalTime.parse(node.path("endTime").asText()),
                            node.path("notes").asText(null),
                            "ACTIVE".equals(rs.getString("status"))
                    );
                }, routineId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy lịch sinh hoạt: " + routineId);
        return rows.getFirst();
    }

    private void validateRoutine(DailyRoutineRequest r) {
        if (r.activityType() == null || r.activityType().isBlank()) throw new BusinessException("Hoạt động sinh hoạt không được để trống");
        if (r.startTime() == null || r.endTime() == null) throw new BusinessException("Thời gian bắt đầu và kết thúc không được để trống");
        if (!r.startTime().isBefore(r.endTime())) throw new BusinessException("Giờ bắt đầu phải trước giờ kết thúc");
    }

    private StableView buildStable(UUID id, String name, String location, boolean active) {
        List<StallView> stalls = jdbc.query("select id,status,data_json from module_records where module_code='STABLE_CARE' "
                        + "and record_type='STALL' and JSON_VALUE(data_json,'$.stableId')=? order by JSON_VALUE(data_json,'$.stallCode')",
                (rs, row) -> {
                    UUID stallId = rs.getObject("id", UUID.class);
                    JsonNode node = read(rs.getString("data_json"));
                    AssignmentView assignment = activeAssignmentForStall(stallId, id, name);
                    return new StallView(stallId, node.path("stallCode").asText(), "ACTIVE".equals(rs.getString("status")), assignment);
                }, id.toString());
        return new StableView(id, name, location, active, stalls);
    }

    private AssignmentView activeAssignmentForStall(UUID stallId, UUID stableId, String stableName) {
        List<AssignmentView> rows = jdbc.query("select top 1 m.id,m.horse_id,m.user_id,m.data_json,h.code horse_code,h.name horse_name,h.health_status "
                        + "from module_records m left join horses h on h.id=m.horse_id "
                        + "where m.module_code='STABLE_CARE' and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE' "
                        + "and JSON_VALUE(m.data_json,'$.stallId')=? order by m.created_at desc",
                (rs, row) -> assignment(rs.getObject("id", UUID.class), rs.getObject("horse_id", UUID.class),
                        rs.getString("horse_code"), rs.getString("horse_name"), rs.getString("health_status"),
                        rs.getObject("user_id", UUID.class), rs.getString("data_json"), stableId, stableName, true),
                stallId.toString());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private AssignmentView requireAssignment(UUID id) {
        List<AssignmentView> rows = jdbc.query("select m.id,m.horse_id,m.user_id,m.status,m.data_json,s.name stable_name,h.code horse_code,h.name horse_name,h.health_status "
                        + "from module_records m join stables s on cast(s.id as varchar(36))=JSON_VALUE(m.data_json,'$.stableId') "
                        + "left join horses h on h.id=m.horse_id "
                        + "where m.id=? and m.record_type='STALL_ASSIGNMENT'",
                (rs, row) -> assignment(rs.getObject("id", UUID.class), rs.getObject("horse_id", UUID.class),
                        rs.getString("horse_code"), rs.getString("horse_name"), rs.getString("health_status"),
                        rs.getObject("user_id", UUID.class), rs.getString("data_json"),
                        UUID.fromString(read(rs.getString("data_json")).path("stableId").asText()),
                        rs.getString("stable_name"), "ACTIVE".equals(rs.getString("status"))), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy phân bổ chuồng: " + id);
        return rows.getFirst();
    }

    private AssignmentView assignment(UUID id, UUID horseId, String horseCode, String horseName, String healthStatus,
                                      UUID actor, String jsonValue, UUID stableId, String stableName, boolean active) {
        JsonNode node = read(jsonValue);
        String until = node.path("assignedUntil").isNull() ? null : node.path("assignedUntil").asText(null);
        return new AssignmentView(id, stableId, stableName, UUID.fromString(node.path("stallId").asText()),
                node.path("stallCode").asText(), horseId, horseCode, horseName, healthStatus, actor,
                OffsetDateTime.parse(node.path("assignedFrom").asText()),
                until == null || until.isBlank() ? null : OffsetDateTime.parse(until), active);
    }

    private HorseSummary getHorseSummary(UUID horseId) {
        List<HorseSummary> rows = jdbc.query("select code, name, health_status from horses where id=?",
                (rs, row) -> new HorseSummary(rs.getString("code"), rs.getString("name"), rs.getString("health_status")),
                horseId);
        return rows.isEmpty() ? new HorseSummary(null, null, null) : rows.getFirst();
    }

    private void addStallInternal(UUID stableId, UUID actor, String code) {
        Integer exists = jdbc.queryForObject("select count(*) from module_records where module_code='STABLE_CARE' "
                + "and record_type='STALL' and JSON_VALUE(data_json,'$.stableId')=? and upper(JSON_VALUE(data_json,'$.stallCode'))=upper(?)",
                Integer.class, stableId.toString(), code);
        if (exists != null && exists > 0) throw new BusinessException("Mã ô chuồng đã tồn tại trong chuồng này: " + code);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into module_records(id,module_code,record_type,source_table,user_id,status,data_json) "
                        + "values(?,'STABLE_CARE','STALL','api',?,'ACTIVE',?)",
                id, actor, toJson(Map.of("stableId", stableId.toString(), "stallCode", code, "isActive", true)));
    }

    private StableView requireActiveStable(UUID stableId) {
        StableView stable = get(stableId);
        if (!stable.active()) throw new BusinessException("Chuồng đang ngừng hoạt động");
        return stable;
    }

    private StallView requireStall(UUID stableId, UUID stallId) {
        return get(stableId).stalls().stream().filter(value -> value.id().equals(stallId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ô chuồng: " + stallId));
    }

    private void endCurrentHorseAssignment(UUID horseId, OffsetDateTime at) {
        jdbc.update("update module_records set status='ENDED',data_json=JSON_MODIFY(data_json,'$.assignedUntil',?),updated_at=SYSDATETIMEOFFSET() "
                        + "where module_code='STABLE_CARE' and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and horse_id=?",
                at.toString(), horseId);
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Dữ liệu JSON chuồng không hợp lệ", exception); }
    }

    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Không thể tạo dữ liệu JSON chuồng", exception); }
    }

    private String normalizeCode(String value) { return required(value, "Mã ô chuồng không được để trống").toUpperCase(Locale.ROOT); }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw new BusinessException(message); return value.trim(); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record StableView(UUID id, String name, String location, boolean active, List<StallView> stalls) {}
    public record StallView(UUID id, String code, boolean active, AssignmentView currentAssignment) {}
    public record AssignmentView(UUID id, UUID stableId, String stableName, UUID stallId, String stallCode,
                                 UUID horseId, String horseCode, String horseName, String horseHealthStatus,
                                 UUID assignedBy, OffsetDateTime assignedFrom,
                                 OffsetDateTime assignedUntil, boolean active) {}
    public record DailyRoutineView(UUID id, UUID horseId, String activityType, LocalTime startTime, LocalTime endTime, String notes, boolean active) {}
    public record DailyRoutineRequest(String activityType, LocalTime startTime, LocalTime endTime, String notes) {}
    private record HorseSummary(String code, String name, String healthStatus) {}
}
