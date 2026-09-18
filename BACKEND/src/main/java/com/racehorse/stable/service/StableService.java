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
        return new AssignmentView(assignmentId, stableId, stable.name(), stallId, stall.code(), horseId,
                actor, now, null, true);
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
                current.stallCode(), current.horseId(), current.assignedBy(), current.assignedFrom(), now, false);
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
        List<AssignmentView> rows = jdbc.query("select top 1 id,horse_id,user_id,data_json from module_records "
                        + "where module_code='STABLE_CARE' and record_type='STALL_ASSIGNMENT' and status='ACTIVE' "
                        + "and JSON_VALUE(data_json,'$.stallId')=? order by created_at desc",
                (rs, row) -> assignment(rs.getObject("id", UUID.class), rs.getObject("horse_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getString("data_json"), stableId, stableName, true),
                stallId.toString());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private AssignmentView requireAssignment(UUID id) {
        List<AssignmentView> rows = jdbc.query("select m.id,m.horse_id,m.user_id,m.status,m.data_json,s.name stable_name "
                        + "from module_records m join stables s on cast(s.id as varchar(36))=JSON_VALUE(m.data_json,'$.stableId') "
                        + "where m.id=? and m.record_type='STALL_ASSIGNMENT'",
                (rs, row) -> assignment(rs.getObject("id", UUID.class), rs.getObject("horse_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getString("data_json"),
                        UUID.fromString(read(rs.getString("data_json")).path("stableId").asText()),
                        rs.getString("stable_name"), "ACTIVE".equals(rs.getString("status"))), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy phân bổ chuồng: " + id);
        return rows.getFirst();
    }

    private AssignmentView assignment(UUID id, UUID horseId, UUID actor, String jsonValue,
                                      UUID stableId, String stableName, boolean active) {
        JsonNode node = read(jsonValue);
        String until = node.path("assignedUntil").isNull() ? null : node.path("assignedUntil").asText(null);
        return new AssignmentView(id, stableId, stableName, UUID.fromString(node.path("stallId").asText()),
                node.path("stallCode").asText(), horseId, actor,
                OffsetDateTime.parse(node.path("assignedFrom").asText()),
                until == null || until.isBlank() ? null : OffsetDateTime.parse(until), active);
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
                                 UUID horseId, UUID assignedBy, OffsetDateTime assignedFrom,
                                 OffsetDateTime assignedUntil, boolean active) {}
}
