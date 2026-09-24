package com.racehorse.caretask.service;

import com.racehorse.caretask.dto.*;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CareTaskService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccess;
    private final ObjectMapper json;

    public CareTaskService(JdbcTemplate jdbc, HorseAccessService horseAccess, ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseAccess = horseAccess;
        this.json = json;
    }

    @Transactional
    public CareTaskResponse createTask(UUID actor, CreateCareTaskRequest request) {
        horseAccess.requireActive(request.horseId());
        validateTask(request);

        UUID taskId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskType", request.taskType().trim().toUpperCase(Locale.ROOT));
        data.put("title", request.title().trim());
        data.put("scheduledDate", request.scheduledDate().toString());
        data.put("scheduledTime", request.scheduledTime().toString());
        data.put("assignedStaffId", request.assignedStaffId() == null ? null : request.assignedStaffId().toString());
        data.put("completedAt", null);
        data.put("completedByUserId", null);
        data.put("completedByName", null);
        data.put("completionNotes", null);
        data.put("photoUrl", null);

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,event_date,data_json,created_at,updated_at) " +
                        "values(?,'STABLE_CARE','DAILY_CARE_TASK','api',?,?,'PENDING',?,?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                taskId, request.horseId(), request.assignedStaffId() == null ? actor : request.assignedStaffId(),
                request.scheduledDate(), toJson(data));

        return getTask(taskId);
    }

    @Transactional
    public CareTaskResponse completeTask(UUID taskId, UUID actor, CompleteCareTaskRequest request) {
        CareTaskResponse existing = getTask(taskId);
        if ("COMPLETED".equals(existing.status())) {
            throw new BusinessException("Nhiệm vụ này đã được hoàn thành trước đó");
        }
        if ("CANCELLED".equals(existing.status())) {
            throw new BusinessException("Không thể hoàn thành nhiệm vụ đã bị hủy");
        }

        List<String> names = jdbc.query(
                "select username from users where id=?",
                (rs, rn) -> rs.getString("username"), actor);
        String staffName = names.isEmpty() ? "Nhân viên chuồng" : names.getFirst();

        OffsetDateTime now = OffsetDateTime.now();
        String notes = request == null || request.notes() == null ? null : request.notes().trim();
        String photoUrl = request == null || request.photoUrl() == null ? null : request.photoUrl().trim();

        String currentJson = jdbc.queryForObject(
                "select data_json from module_records where id=?", String.class, taskId);
        Map<String, Object> data = readMap(currentJson);
        data.put("completedAt", now.toString());
        data.put("completedByUserId", actor.toString());
        data.put("completedByName", staffName);
        data.put("completionNotes", notes);
        data.put("photoUrl", photoUrl);

        jdbc.update(
                "update module_records set status='COMPLETED', data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                toJson(data), taskId);

        return getTask(taskId);
    }

    @Transactional
    public CareTaskResponse cancelTask(UUID taskId, UUID actor) {
        CareTaskResponse existing = getTask(taskId);
        if ("COMPLETED".equals(existing.status())) {
            throw new BusinessException("Không thể hủy nhiệm vụ đã hoàn thành");
        }
        jdbc.update(
                "update module_records set status='CANCELLED', updated_at=SYSDATETIMEOFFSET() where id=?",
                taskId);
        return getTask(taskId);
    }

    @Transactional(readOnly = true)
    public CareTaskResponse getTask(UUID taskId) {
        List<CareTaskResponse> rows = jdbc.query(
                baseSelect() + " where m.id=? and m.module_code='STABLE_CARE' and m.record_type='DAILY_CARE_TASK'",
                (rs, rowNum) -> mapResponse(rs), taskId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy nhiệm vụ chăm sóc: " + taskId);
        }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<CareTaskResponse> listTasks(UUID horseId, UUID assignedStaffId, LocalDate date, String status, Boolean overdueOnly) {
        StringBuilder sql = new StringBuilder(baseSelect())
                .append(" where m.module_code='STABLE_CARE' and m.record_type='DAILY_CARE_TASK'");
        List<Object> args = new ArrayList<>();

        if (horseId != null) {
            sql.append(" and m.horse_id=?");
            args.add(horseId);
        }
        if (assignedStaffId != null) {
            sql.append(" and JSON_VALUE(m.data_json,'$.assignedStaffId')=?");
            args.add(assignedStaffId.toString());
        }
        if (date != null) {
            sql.append(" and m.event_date=?");
            args.add(date);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and m.status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }

        sql.append(" order by m.event_date asc, JSON_VALUE(m.data_json,'$.scheduledTime') asc");

        List<CareTaskResponse> list = jdbc.query(sql.toString(), (rs, rowNum) -> mapResponse(rs), args.toArray());

        if (Boolean.TRUE.equals(overdueOnly)) {
            return list.stream().filter(CareTaskResponse::isOverdue).toList();
        }
        return list;
    }

    private String baseSelect() {
        return "select m.id, m.horse_id, h.code horse_code, h.name horse_name, m.user_id, m.status, " +
                "m.event_date, m.data_json, m.created_at " +
                "from module_records m " +
                "left join horses h on h.id = m.horse_id ";
    }

    private CareTaskResponse mapResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID horseId = rs.getObject("horse_id", UUID.class);
        String horseCode = rs.getString("horse_code");
        String horseName = rs.getString("horse_name");
        String status = rs.getString("status");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);

        JsonNode node = read(rs.getString("data_json"));
        String taskType = node.path("taskType").asText("OTHER");
        String title = node.path("title").asText("");
        LocalDate scheduledDate = LocalDate.parse(node.path("scheduledDate").asText());
        LocalTime scheduledTime = LocalTime.parse(node.path("scheduledTime").asText());

        String assignedStaffStr = node.path("assignedStaffId").isNull() ? null : node.path("assignedStaffId").asText(null);
        UUID assignedStaffId = assignedStaffStr == null || assignedStaffStr.isBlank() ? null : UUID.fromString(assignedStaffStr);

        String completedAtStr = node.path("completedAt").isNull() ? null : node.path("completedAt").asText(null);
        OffsetDateTime completedAt = completedAtStr == null || completedAtStr.isBlank() ? null : OffsetDateTime.parse(completedAtStr);

        String completedByStr = node.path("completedByUserId").isNull() ? null : node.path("completedByUserId").asText(null);
        UUID completedByUserId = completedByStr == null || completedByStr.isBlank() ? null : UUID.fromString(completedByStr);

        String completedByName = node.path("completedByName").isNull() ? null : node.path("completedByName").asText(null);
        String completionNotes = node.path("completionNotes").isNull() ? null : node.path("completionNotes").asText(null);
        String photoUrl = node.path("photoUrl").isNull() ? null : node.path("photoUrl").asText(null);

        // Check if overdue
        boolean isOverdue = false;
        if ("PENDING".equals(status)) {
            LocalDate today = LocalDate.now();
            LocalTime nowTime = LocalTime.now();
            isOverdue = scheduledDate.isBefore(today) || (scheduledDate.isEqual(today) && scheduledTime.isBefore(nowTime));
        }

        return new CareTaskResponse(
                id, horseId, horseCode, horseName, taskType, title, scheduledDate, scheduledTime,
                assignedStaffId, status, isOverdue, completedAt, completedByUserId, completedByName,
                completionNotes, photoUrl, createdAt
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        try {
            return new LinkedHashMap<>(json.readValue(value, Map.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dữ liệu JSON nhiệm vụ chăm sóc không hợp lệ", exception);
        }
    }

    private void validateTask(CreateCareTaskRequest r) {
        if (r.taskType() == null || r.taskType().isBlank()) throw new BusinessException("Loại nhiệm vụ không được để trống");
        if (r.title() == null || r.title().isBlank()) throw new BusinessException("Tiêu đề nhiệm vụ không được để trống");
        if (r.scheduledDate() == null) throw new BusinessException("Ngày lên lịch không được để trống");
        if (r.scheduledTime() == null) throw new BusinessException("Giờ lên lịch không được để trống");
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Dữ liệu JSON nhiệm vụ chăm sóc không hợp lệ", exception); }
    }

    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Không thể tạo dữ liệu JSON nhiệm vụ chăm sóc", exception); }
    }
}
