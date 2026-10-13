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
<<<<<<< Updated upstream
=======

        validateHealthCompatibility(stableId, horseId, horse.getHealthStatus());

>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
        HorseSummary horse = getHorseSummary(horseId);
=======
        createDefaultDailyRoutineIfAbsent(horseId, horse, actor);
>>>>>>> Stashed changes
        return new AssignmentView(assignmentId, stableId, stable.name(), stallId, stall.code(), horseId,
                horse.code(), horse.name(), horse.healthStatus(), actor, now, null, true);
    }

    @Transactional
    public AssignmentView transferHorse(UUID horseId, UUID targetStableId, UUID targetStallId, UUID actor, String reason) {
        StableView targetStable = requireActiveStable(targetStableId);
        StallView targetStall = requireStall(targetStableId, targetStallId);
        if (!targetStall.active()) throw new BusinessException("Ô chuồng đích đang ngừng hoạt động");

        // Khóa bi quan
        jdbc.queryForObject("select id from module_records with (UPDLOCK, HOLDLOCK) where id=? and module_code='STABLE_CARE' and record_type='STALL'", UUID.class, targetStallId);
        jdbc.queryForObject("select id from stables with (UPDLOCK, HOLDLOCK) where id=?", UUID.class, targetStableId);
        jdbc.queryForObject("select id from horses with (UPDLOCK, HOLDLOCK) where id=?", UUID.class, horseId);

        Horse horse = horseAccess.requireServiceEligible(horseId);

        if (horse.getDiagnosingVet() == null) {
            throw new BusinessException("Bác sĩ phải hoàn tất khám trước khi nhân viên phân ô hoặc chuyển ô chuồng");
        }

        // Tìm phân bổ hiện tại của ngựa
        List<AssignmentView> currentList = jdbc.query(
                "select m.id, m.horse_id, m.user_id, m.status, m.data_json, s.name as stable_name, "
                + "h.name as horse_name, h.code as horse_code, h.health_status, h.status as horse_status, h.health_source_type, "
                + "(select count(*) from training_locks tl where tl.horse_id=h.id and tl.released_at is null) as active_lock_count, "
                + "(select top 1 reason from training_locks tl where tl.horse_id=h.id and tl.released_at is null order by tl.locked_at desc) as active_lock_reason "
                + "from module_records m with (UPDLOCK, HOLDLOCK) "
                + "join stables s on cast(s.id as varchar(36))=JSON_VALUE(m.data_json,'$.stableId') "
                + "left join horses h on h.id=m.horse_id "
                + "where m.horse_id=? and m.module_code='STABLE_CARE' and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE'",
                (rs, row) -> {
                    String healthStatus = rs.getString("health_status");
                    String healthSourceType = rs.getString("health_source_type");
                    String horseStatus = rs.getString("horse_status");
                    int lockCount = rs.getInt("active_lock_count");
                    String lockReason = rs.getString("active_lock_reason");
                    boolean trainingLocked = lockCount > 0;
                    boolean emergencyActive = "INJURED".equalsIgnoreCase(healthStatus)
                            || "INJURED".equalsIgnoreCase(horseStatus)
                            || "TRAINING_EMERGENCY".equalsIgnoreCase(healthSourceType);
                    return assignment(rs.getObject("id", UUID.class), rs.getObject("horse_id", UUID.class),
                            rs.getString("horse_name"), rs.getString("horse_code"),
                            rs.getObject("user_id", UUID.class), rs.getString("data_json"),
                            UUID.fromString(read(rs.getString("data_json")).path("stableId").asText()),
                            rs.getString("stable_name"), true,
                            healthStatus != null && !healthStatus.isBlank() ? healthStatus : "ACTIVE",
                            trainingLocked, lockReason, emergencyActive);
                }, horseId);

        if (currentList.isEmpty() || !"STABLED".equals(horse.getIntakeStatus())) {
            throw new BusinessException("Ngựa chưa được xếp ô chuồng nên không thể thực hiện chuyển ô");
        }

        AssignmentView currentAssignment = currentList.getFirst();

        // Chặn chuyển trùng ô chuồng hiện tại
        if (currentAssignment.stallId().equals(targetStallId)) {
            throw new BusinessException("Ô chuồng đích phải khác ô chuồng hiện tại");
        }

        // Kiểm tra sức chứa khu chuồng đích: tối đa 4 con ngựa
        Integer occupiedInStable = jdbc.queryForObject(
                "select count(distinct horse_id) from module_records with (UPDLOCK, HOLDLOCK) where module_code='STABLE_CARE' "
                + "and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stableId')=? and horse_id<>?",
                Integer.class, targetStableId.toString(), horseId);
        if (occupiedInStable != null && occupiedInStable >= 4) {
            throw new BusinessException("Khu chuồng này đã đạt sức chứa tối đa 4 con ngựa");
        }

        // Kiểm tra ô chuồng đích: đã có ngựa khác chưa
        Integer occupiedInStall = jdbc.queryForObject(
                "select count(*) from module_records with (UPDLOCK, HOLDLOCK) where module_code='STABLE_CARE' "
                + "and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and JSON_VALUE(data_json,'$.stallId')=? and horse_id<>?",
                Integer.class, targetStallId.toString(), horseId);
        if (occupiedInStall != null && occupiedInStall > 0) {
            throw new BusinessException("Ô chuồng đã có ngựa");
        }

        // Kiểm tra tương thích sức khỏe / cách ly
        validateHealthCompatibility(targetStableId, horseId, horse.getHealthStatus());

        OffsetDateTime now = OffsetDateTime.now();
        String cleanReason = reason != null && !reason.isBlank() ? reason.trim() : "Chuyển ô chuồng";

        // Kết thúc phân bổ cũ, lưu lý do chuyển và người thực hiện
        jdbc.update(
                "update module_records set status='ENDED', "
                + "data_json=JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(data_json,'$.assignedUntil',?),'$.endedByUserId',?),'$.endedReason',?), "
                + "updated_at=SYSDATETIMEOFFSET() "
                + "where id=?",
                now.toString(), actor.toString(), "TRANSFERRED: " + cleanReason, currentAssignment.id());

        // Tạo phân bổ mới với thông tin chuyển ô
        UUID newAssignmentId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stableId", targetStableId.toString());
        data.put("stallId", targetStallId.toString());
        data.put("stallCode", targetStall.code());
        data.put("assignedFrom", now.toString());
        data.put("assignedUntil", null);
        data.put("previousStableId", currentAssignment.stableId().toString());
        data.put("previousStallId", currentAssignment.stallId().toString());
        data.put("previousStallCode", currentAssignment.stallCode());
        data.put("transferReason", cleanReason);
        data.put("transferredBy", actor.toString());

        try {
            jdbc.update("insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,event_date,data_json) "
                            + "values(?,'STABLE_CARE','STALL_ASSIGNMENT','api',?,?,'ACTIVE',cast(? as date),?)",
                    newAssignmentId, horseId, actor, now, toJson(data));
        } catch (DataAccessException exception) {
            String message = exception.getMessage() != null ? exception.getMessage() : "";
            if (message.contains("52001") || message.contains("không thể phân bổ trùng") || message.contains("đã có phân bổ hoạt động")) {
                throw new BusinessException("Ô chuồng đã có phân bổ hoạt động, không thể phân bổ trùng");
            }
            if (message.contains("52002") || message.contains("Ngựa đã có phân bổ ô chuồng đang hoạt động")) {
                throw new BusinessException("Ngựa đã có phân bổ ô chuồng đang hoạt động");
            }
            if (message.contains("52003") || message.contains("đã đạt sức chứa tối đa 4 con ngựa")) {
                throw new BusinessException("Khu chuồng này đã đạt sức chứa tối đa 4 con ngựa");
            }
            throw exception;
        }

        // Cập nhật vị trí mới trên hồ sơ ngựa, giữ nguyên intake_status = 'STABLED'
        jdbc.update("update horses set stable_location=?,intake_status='STABLED',updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                targetStable.name() + " / " + targetStall.code(), horseId);

        return new AssignmentView(newAssignmentId, targetStableId, targetStable.name(), targetStallId, targetStall.code(),
                horseId, horse.getName(), horse.getCode(), actor, now, null, true,
                currentAssignment.horseHealthStatus(), currentAssignment.trainingLocked(),
                currentAssignment.trainingLockReason(), currentAssignment.emergencyActive());
    }

    private void validateHealthCompatibility(UUID targetStableId, UUID horseId, String horseHealthStatus) {
        String normalizedHealth = horseHealthStatus != null && !horseHealthStatus.isBlank()
                ? horseHealthStatus.toUpperCase(Locale.ROOT)
                : "FIT";
        List<String> existingHealthStatuses = jdbc.query(
                "select distinct coalesce(h.health_status, 'FIT') as health_status from module_records m "
                + "join horses h on h.id = m.horse_id "
                + "where m.module_code = 'STABLE_CARE' and m.record_type = 'STALL_ASSIGNMENT' and m.status = 'ACTIVE' "
                + "and JSON_VALUE(m.data_json, '$.stableId') = ? and m.horse_id <> ?",
                (rs, row) -> {
                    String s = rs.getString("health_status");
                    return s != null ? s.toUpperCase(Locale.ROOT) : "FIT";
                },
                targetStableId.toString(), horseId);

        if ("QUARANTINED".equals(normalizedHealth)) {
            boolean hasNonQuarantined = existingHealthStatuses.stream().anyMatch(s -> !"QUARANTINED".equals(s));
            if (hasNonQuarantined) {
                throw new BusinessException("Ngựa đang trong diện cách ly (QUARANTINED) không thể xếp chung khu chuồng với ngựa bình thường");
            }
        } else {
            boolean hasQuarantined = existingHealthStatuses.stream().anyMatch("QUARANTINED"::equals);
            if (hasQuarantined) {
                throw new BusinessException("Khu chuồng đích đang có ngựa cách ly (QUARANTINED), không thể phân bổ ngựa vào khu vực này");
            }
        }
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
        Horse horse = horseAccess.requireActive(horseId);
        validateRoutine(request);
<<<<<<< Updated upstream
        UUID id = UUID.randomUUID();
=======
        validateRoutineAllowedForHorse(horseId, horse, request);
        UUID routineId = UUID.randomUUID();
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
        validateRoutine(request);
=======
        Horse horse = horseAccess.requireActive(existing.horseId());
        validateRoutineAllowedForHorse(existing.horseId(), horse, request);

>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
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

=======
    private void validateRoutineAllowedForHorse(UUID horseId, Horse horse, DailyRoutineRequest r) {
        String act = r.activityType().trim().toUpperCase(Locale.ROOT);
        if ("TURNOUT".equals(act)) {
            boolean isInjured = horse != null && (
                    "INJURED".equalsIgnoreCase(horse.getHealthStatus())
                    || (horse.getStatus() != null && "INJURED".equalsIgnoreCase(horse.getStatus().name()))
            );
            Integer lockCount = jdbc != null ? jdbc.queryForObject(
                    "select count(*) from training_locks where horse_id=? and released_at is null",
                    Integer.class, horseId) : 0;
            boolean isLocked = lockCount != null && lockCount > 0;
            if (isInjured || isLocked) {
                throw new BusinessException("Ngựa đang trong trạng thái chấn thương hoặc bị khóa y tế, không được phép xếp lịch thả bãi (TURNOUT)");
            }
        }
    }

    @Transactional
    public List<DailyRoutineView> createDefaultDailyRoutineIfAbsent(UUID horseId, UUID actor) {
        return createDefaultDailyRoutineIfAbsent(horseId, null, actor);
    }

    @Transactional
    public List<DailyRoutineView> createDefaultDailyRoutineIfAbsent(UUID horseId, Horse horseOpt, UUID actor) {
        Integer existingCount = jdbc != null ? jdbc.queryForObject(
                "select count(*) from module_records where module_code='STABLE_CARE' and record_type='HORSE_DAILY_ROUTINE' and horse_id=? and status='ACTIVE'",
                Integer.class, horseId) : 0;
        if (existingCount != null && existingCount > 0) {
            return listDailyRoutines(horseId);
        }

        Horse horse = horseOpt;
        if (horse == null && horseAccess != null) {
            try {
                horse = horseAccess.requireActive(horseId);
            } catch (Exception ignored) {
            }
        }
        String healthStatus = horse != null ? horse.getHealthStatus() : null;
        String horseStatus = (horse != null && horse.getStatus() != null) ? horse.getStatus().name() : null;
        if (healthStatus == null && jdbc != null) {
            try {
                List<String> hs = jdbc.query("select health_status from horses where id=?", (rs, r) -> rs.getString("health_status"), horseId);
                if (!hs.isEmpty()) healthStatus = hs.getFirst();
            } catch (Exception ignored) {
            }
        }

        boolean isInjured = "INJURED".equalsIgnoreCase(healthStatus)
                || "INJURED".equalsIgnoreCase(horseStatus)
                || "QUARANTINED".equalsIgnoreCase(healthStatus);
        Integer lockCount = jdbc != null ? jdbc.queryForObject(
                "select count(*) from training_locks where horse_id=? and released_at is null",
                Integer.class, horseId) : 0;
        boolean hasLock = lockCount != null && lockCount > 0;

        String templateCode = (isInjured || hasLock) ? "TEMPLATE_STALL_REST" : "TEMPLATE_DEFAULT_HEALTHY";

        List<String> templateData = jdbc.query(
                "select data_json from module_records where module_code='STABLE_CARE' and record_type='DAILY_ROUTINE_TEMPLATE' and status='ACTIVE' and JSON_VALUE(data_json,'$.templateCode')=?",
                (rs, row) -> rs.getString("data_json"), templateCode);

        List<DailyRoutineRequest> activities = new ArrayList<>();
        if (!templateData.isEmpty()) {
            JsonNode root = read(templateData.getFirst());
            JsonNode actsNode = root.path("activities");
            if (actsNode.isArray()) {
                for (JsonNode a : actsNode) {
                    activities.add(new DailyRoutineRequest(
                            a.path("activityType").asText(),
                            LocalTime.parse(a.path("startTime").asText()),
                            LocalTime.parse(a.path("endTime").asText()),
                            a.path("notes").isNull() ? null : a.path("notes").asText(null)
                    ));
                }
            }
        }

        if (activities.isEmpty()) {
            if (isInjured || hasLock) {
                activities.add(new DailyRoutineRequest("STALL_REST", LocalTime.of(7, 0), LocalTime.of(8, 0), "Nghỉ ngơi tĩnh tại ô chuồng theo chỉ định y tế - không thả bãi"));
                activities.add(new DailyRoutineRequest("ICE_BOOTS", LocalTime.of(9, 30), LocalTime.of(10, 30), "Chườm đá lạnh & ngâm chân thư giãn cơ gân"));
                activities.add(new DailyRoutineRequest("REST", LocalTime.of(11, 30), LocalTime.of(14, 0), "Nghỉ ngơi dưỡng thương tại chuồng"));
                activities.add(new DailyRoutineRequest("GROOMING", LocalTime.of(16, 0), LocalTime.of(16, 45), "Chải lông nhẹ nhàng và kiểm tra khớp chân"));
            } else {
                activities.add(new DailyRoutineRequest("GROOMING", LocalTime.of(6, 0), LocalTime.of(7, 0), "Chải lông & vệ sinh chuẩn bị ca sáng"));
                activities.add(new DailyRoutineRequest("TURNOUT", LocalTime.of(7, 30), LocalTime.of(8, 30), "Thả bãi dạo tự do tại bãi cỏ tiêu chuẩn"));
                activities.add(new DailyRoutineRequest("REST", LocalTime.of(11, 30), LocalTime.of(13, 30), "Nghỉ trưa tĩnh tại ô chuồng"));
                activities.add(new DailyRoutineRequest("WALKING", LocalTime.of(16, 0), LocalTime.of(17, 0), "Dắt đi dạo nhẹ thư giãn gân cốt"));
            }
        }

        for (DailyRoutineRequest act : activities) {
            UUID routineId = UUID.randomUUID();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("activityType", act.activityType().trim().toUpperCase(Locale.ROOT));
            data.put("startTime", act.startTime().toString());
            data.put("endTime", act.endTime().toString());
            data.put("notes", trimToNull(act.notes()));
            data.put("isActive", true);
            data.put("generatedFromTemplate", templateCode);

            jdbc.update("insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,data_json,created_at,updated_at) "
                            + "values(?,'STABLE_CARE','HORSE_DAILY_ROUTINE','api',?,?,'ACTIVE',?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                    routineId, horseId, actor, toJson(data));
        }

        return listDailyRoutines(horseId);
    }

    @Transactional(readOnly = true)
    public RoutineBackfillPreview previewRoutineBackfill() {
        List<Map<String, Object>> stabledHorses = jdbc.queryForList(
                "select h.id, h.code, h.name, h.stable_location, h.health_status, h.status as horse_status, h.intake_status, "
                        + "(select count(*) from training_locks tl where tl.horse_id=h.id and tl.released_at is null) as active_locks, "
                        + "(select count(*) from module_records mr where mr.module_code='STABLE_CARE' and mr.record_type='HORSE_DAILY_ROUTINE' and mr.horse_id=h.id and mr.status='ACTIVE') as routine_count "
                        + "from horses h "
                        + "where h.stable_location is not null and h.stable_location <> '' "
                        + "order by h.code asc");

        int totalStabled = stabledHorses.size();
        int alreadyConfigured = 0;
        int eligibleHealthy = 0;
        int eligibleRestricted = 0;
        int excluded = 0;
        List<RoutineBackfillItem> details = new ArrayList<>();

        for (Map<String, Object> row : stabledHorses) {
            UUID hId = (UUID) row.get("id");
            String code = (String) row.get("code");
            String name = (String) row.get("name");
            String stableLoc = (String) row.get("stable_location");
            String health = (String) row.get("health_status");
            String intakeStatus = (String) row.get("intake_status");
            int locks = ((Number) row.get("active_locks")).intValue();
            int routines = ((Number) row.get("routine_count")).intValue();

            boolean hasLock = locks > 0;
            boolean isInjured = "INJURED".equalsIgnoreCase(health) || "QUARANTINED".equalsIgnoreCase(health);

            if (!"STABLED".equalsIgnoreCase(intakeStatus) || stableLoc.equalsIgnoreCase("Khu chuồng trại")) {
                excluded++;
                details.add(new RoutineBackfillItem(hId, code, name, stableLoc, health, hasLock, routines,
                        "EXCLUDED_NOT_STABLED", "NONE"));
            } else if (routines > 0) {
                alreadyConfigured++;
                details.add(new RoutineBackfillItem(hId, code, name, stableLoc, health, hasLock, routines,
                        "SKIPPED_ALREADY_CONFIGURED", "PRESERVE_EXISTING"));
            } else if (isInjured || hasLock) {
                eligibleRestricted++;
                details.add(new RoutineBackfillItem(hId, code, name, stableLoc, health, hasLock, routines,
                        "ELIGIBLE_STALL_REST", "TEMPLATE_STALL_REST"));
            } else {
                eligibleHealthy++;
                details.add(new RoutineBackfillItem(hId, code, name, stableLoc, health, hasLock, routines,
                        "ELIGIBLE_DEFAULT_HEALTHY", "TEMPLATE_DEFAULT_HEALTHY"));
            }
        }

        return new RoutineBackfillPreview(totalStabled, alreadyConfigured, eligibleHealthy, eligibleRestricted, excluded, details);
    }

    @Transactional
    public RoutineBackfillPreview executeRoutineBackfill(UUID actor) {
        RoutineBackfillPreview preview = previewRoutineBackfill();
        for (RoutineBackfillItem item : preview.details()) {
            if ("ELIGIBLE_DEFAULT_HEALTHY".equals(item.statusAction()) || "ELIGIBLE_STALL_REST".equals(item.statusAction())) {
                createDefaultDailyRoutineIfAbsent(item.horseId(), actor);
            }
        }
        return previewRoutineBackfill();
    }

>>>>>>> Stashed changes
    public record StableView(UUID id, String name, String location, boolean active, List<StallView> stalls) {}
    public record StallView(UUID id, String code, boolean active, AssignmentView currentAssignment) {}
    public record AssignmentView(UUID id, UUID stableId, String stableName, UUID stallId, String stallCode,
                                 UUID horseId, String horseCode, String horseName, String horseHealthStatus,
                                 UUID assignedBy, OffsetDateTime assignedFrom,
                                 OffsetDateTime assignedUntil, boolean active) {}
    public record DailyRoutineView(UUID id, UUID horseId, String activityType, LocalTime startTime, LocalTime endTime, String notes, boolean active) {}
    public record DailyRoutineRequest(String activityType, LocalTime startTime, LocalTime endTime, String notes) {}
<<<<<<< Updated upstream
    private record HorseSummary(String code, String name, String healthStatus) {}
=======
    public record RoutineBackfillPreview(int totalStabledHorses, int alreadyConfiguredCount, int eligibleHealthyCount,
                                         int eligibleRestrictedCount, int excludedCount, List<RoutineBackfillItem> details) {}
    public record RoutineBackfillItem(UUID horseId, String horseCode, String horseName, String stableLocation,
                                      String healthStatus, boolean hasActiveLock, int existingRoutineCount,
                                      String statusAction, String targetTemplate) {}
>>>>>>> Stashed changes
}
