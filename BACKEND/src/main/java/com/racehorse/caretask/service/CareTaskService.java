package com.racehorse.caretask.service;

import com.racehorse.caretask.dto.*;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class CareTaskService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccess;
    private final ZoneId clubZone = ZoneId.of("Asia/Ho_Chi_Minh");

    public CareTaskService(JdbcTemplate jdbc, HorseAccessService horseAccess) {
        this.jdbc = jdbc;
        this.horseAccess = horseAccess;
    }

    @Transactional
    public void ensureRoutineInstancesForDate(LocalDate date) {
        if (date == null) return;

        String sql = """
            INSERT INTO dbo.care_tasks (
                id, routine_definition_id, task_category, scope, horse_id,
                title, description, task_type, scheduled_date, scheduled_time, status,
                created_at, updated_at
            )
            SELECT
                NEWID(), d.id, 'ROUTINE', d.scope, NULL,
                d.title, d.description, d.task_type, ?, d.scheduled_time, 'PENDING',
                SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
            FROM dbo.care_routine_definitions d
            WHERE d.is_active = 1
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.care_tasks t
                  WHERE t.routine_definition_id = d.id
                    AND t.scheduled_date = ?
                    AND t.horse_id IS NULL
              )
            """;
        try {
            jdbc.update(sql, java.sql.Date.valueOf(date), java.sql.Date.valueOf(date));
        } catch (DataIntegrityViolationException ex) {
            // Idempotent catch-up: another concurrent request already inserted the routine instances
        }
    }

    @Transactional
    public void createRecoveryTask(com.racehorse.training.entity.TrainingSchedule schedule, UUID staffId) {
        if (schedule == null || staffId == null || schedule.getHorse() == null) return;
        LocalDate date = schedule.getScheduledStart().atZone(clubZone).toLocalDate();
        LocalTime time = schedule.getScheduledEnd() != null
                ? schedule.getScheduledEnd().atZone(clubZone).toLocalTime()
                : LocalTime.of(11, 0);

        String title = "Hồi phục sau buổi tập - " + schedule.getHorse().getName();
        String description = "Chăm sóc, làm mát gân chân và kiểm tra thể trạng sau phiên tập: " + schedule.getTaskName();

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.care_tasks WHERE horse_id = ? AND scheduled_date = ? AND title = ?",
                Integer.class,
                schedule.getHorse().getId(),
                java.sql.Date.valueOf(date),
                title
        );
        if (count != null && count > 0) return;

        String sql = """
            INSERT INTO dbo.care_tasks (
                id, routine_definition_id, task_category, scope, horse_id,
                title, description, task_type, scheduled_date, scheduled_time,
                assigned_staff_id, status, created_by_user_id, created_at, updated_at
            ) VALUES (
                NEWID(), NULL, 'SPECIAL', 'HORSE', ?,
                ?, ?, 'HEALTH_CHECK', ?, ?,
                ?, 'PENDING', ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
            )
            """;
        try {
            jdbc.update(
                    sql,
                    schedule.getHorse().getId(),
                    title,
                    description,
                    java.sql.Date.valueOf(date),
                    java.sql.Time.valueOf(time),
                    staffId,
                    staffId
            );
        } catch (Exception ex) {
            // Idempotent catch
        }
    }

    @Transactional
    public CareTaskResponse createTask(UUID actor, CreateCareTaskRequest request) {
        if (request.horseId() != null) {
            horseAccess.requireActive(request.horseId());
        }
        validateTask(request);

        UUID taskId = UUID.randomUUID();
        String scope = request.horseId() != null ? "HORSE" : "FACILITY";

        String sql = """
            INSERT INTO dbo.care_tasks (
                id, routine_definition_id, task_category, scope, horse_id,
                title, description, task_type, scheduled_date, scheduled_time,
                assigned_staff_id, status, created_by_user_id, created_at, updated_at
            ) VALUES (
                ?, NULL, 'SPECIAL', ?, ?,
                ?, NULL, ?, ?, ?,
                ?, 'PENDING', ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
            )
            """;

        jdbc.update(
                sql,
                taskId, scope, request.horseId(),
                request.title().trim(), request.taskType().trim().toUpperCase(Locale.ROOT),
                java.sql.Date.valueOf(request.scheduledDate()),
                java.sql.Time.valueOf(request.scheduledTime()),
                request.assignedStaffId(),
                actor
        );

        return getTask(taskId);
    }

    @Transactional
    public CareTaskResponse completeTask(UUID taskId, UUID actor, CompleteCareTaskRequest request) {
        return completeTask(taskId, actor, request, false);
    }

    @Transactional
    public CareTaskResponse completeTask(UUID taskId, UUID actor, CompleteCareTaskRequest request, boolean manager) {
        String notes = request == null || request.notes() == null ? null : request.notes().trim();
        String photoUrl = request == null || request.photoUrl() == null ? null : request.photoUrl().trim();
        return executeCompleteTask(taskId, actor, notes, photoUrl, null, null, manager);
    }

    @Transactional
    public CareTaskResponse completeTask(UUID taskId, UUID actor, String notes, MultipartFile file) {
        return completeTask(taskId, actor, notes, file, false);
    }

    @Transactional
    public CareTaskResponse completeTask(UUID taskId, UUID actor, String notes, MultipartFile file, boolean manager) {
        String cleanNotes = notes == null ? null : notes.trim();
        byte[] photoBytes = null;
        String contentType = null;
        String photoUrl = null;

        if (file != null && !file.isEmpty()) {
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new BusinessException("Dung lượng ảnh không được vượt quá 10MB");
            }
            contentType = file.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new BusinessException("Tệp tải lên phải là hình ảnh (JPEG, PNG, WEBP,...)");
            }
            try {
                photoBytes = file.getBytes();
            } catch (Exception e) {
                throw new BusinessException("Không thể đọc tệp hình ảnh tải lên: " + e.getMessage());
            }
            photoUrl = "/api/care-tasks/" + taskId + "/photo";
        }

        return executeCompleteTask(taskId, actor, cleanNotes, photoUrl, photoBytes, contentType, manager);
    }

    private CareTaskResponse executeCompleteTask(
            UUID taskId, UUID actor, String notes, String photoUrl, byte[] photoBytes, String photoContentType,
            boolean manager) {
        CareTaskResponse existing = getTask(taskId);
        if ("COMPLETED".equals(existing.status())) {
            throw new BusinessException("Nhiệm vụ này đã được hoàn thành trước đó");
        }
        if ("CANCELLED".equals(existing.status())) {
            throw new BusinessException("Không thể hoàn thành nhiệm vụ đã bị hủy");
        }
        boolean sharedRoutine = existing.assignedStaffId() == null && "ROUTINE".equals(existing.taskCategory());
        if (!manager && !actor.equals(existing.assignedStaffId()) && !sharedRoutine) {
            throw new BusinessException("Bạn không được phân công nhiệm vụ chăm sóc này");
        }

        List<String> names = jdbc.query(
                "SELECT username FROM dbo.users WHERE id = ?",
                (rs, rn) -> rs.getString("username"), actor);
        String staffName = names.isEmpty() ? "Nhân viên chuồng" : names.getFirst();

        int updated = jdbc.update(
                """
                UPDATE dbo.care_tasks
                SET status = 'COMPLETED',
                    assigned_staff_id = CASE WHEN ? = 1 THEN assigned_staff_id ELSE COALESCE(assigned_staff_id, ?) END,
                    completed_at = SYSDATETIMEOFFSET(),
                    completed_by_user_id = ?,
                    completed_by_name = ?,
                    completion_notes = ?,
                    photo_url = ?,
                    photo_bytes = ?,
                    photo_content_type = ?,
                    updated_at = SYSDATETIMEOFFSET()
                WHERE id = ? AND status = 'PENDING'
                  AND (? = 1 OR assigned_staff_id = ?
                       OR (assigned_staff_id IS NULL AND task_category = 'ROUTINE'))
                """,
                manager, actor, actor, staffName, notes, photoUrl, photoBytes, photoContentType,
                taskId, manager, actor
        );
        if (updated != 1) {
            throw new BusinessException("Nhiệm vụ đã thay đổi trạng thái hoặc không còn được phân công cho bạn");
        }

        return getTask(taskId);
    }

    public record TaskPhotoData(byte[] bytes, String contentType, String photoUrl, String title) {}

    @Transactional(readOnly = true)
    public TaskPhotoData getTaskPhoto(UUID taskId) {
        String sql = "SELECT photo_bytes, photo_content_type, photo_url, title FROM dbo.care_tasks WHERE id = ?";
        List<TaskPhotoData> list = jdbc.query(sql, (rs, rn) -> {
            byte[] bytes = rs.getBytes("photo_bytes");
            String contentType = rs.getString("photo_content_type");
            String photoUrl = rs.getString("photo_url");
            String title = rs.getString("title");
            return new TaskPhotoData(bytes, contentType, photoUrl, title);
        }, taskId);

        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy nhiệm vụ: " + taskId);
        }
        TaskPhotoData data = list.getFirst();
        if ((data.bytes() == null || data.bytes().length == 0) && (data.photoUrl() == null || data.photoUrl().isBlank())) {
            throw new ResourceNotFoundException("Nhiệm vụ này không có ảnh đính kèm");
        }
        return data;
    }

    @Transactional
    public CareTaskResponse cancelTask(UUID taskId, UUID actor) {
        CareTaskResponse existing = getTask(taskId);
        if ("COMPLETED".equals(existing.status())) {
            throw new BusinessException("Không thể hủy nhiệm vụ đã hoàn thành");
        }
        int updated = jdbc.update(
                "UPDATE dbo.care_tasks SET status = 'CANCELLED', updated_at = SYSDATETIMEOFFSET() WHERE id = ? AND status = 'PENDING'",
                taskId);
        if (updated != 1) {
            throw new BusinessException("Nhiệm vụ đã thay đổi trạng thái; không thể hủy");
        }
        return getTask(taskId);
    }

    @Transactional(readOnly = true)
    public CareTaskResponse getTask(UUID taskId) {
        List<CareTaskResponse> rows = jdbc.query(
                baseSelect() + " WHERE t.id = ?",
                (rs, rowNum) -> mapResponse(rs), taskId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy nhiệm vụ chăm sóc: " + taskId);
        }
        return rows.getFirst();
    }

    @Transactional
    public List<CareTaskResponse> listTasks(UUID horseId, UUID assignedStaffId, LocalDate date, String status, Boolean overdueOnly) {
        if (date != null) {
            ensureRoutineInstancesForDate(date);
        }

        StringBuilder sql = new StringBuilder(baseSelect()).append(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (horseId != null) {
            sql.append(" AND t.horse_id = ?");
            args.add(horseId);
        }
        if (assignedStaffId != null) {
            sql.append(" AND t.assigned_staff_id = ?");
            args.add(assignedStaffId);
        }
        if (date != null) {
            sql.append(" AND t.scheduled_date = ?");
            args.add(java.sql.Date.valueOf(date));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND t.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }

        sql.append(" ORDER BY t.scheduled_date ASC, t.scheduled_time ASC, t.created_at ASC");

        List<CareTaskResponse> list = jdbc.query(sql.toString(), (rs, rowNum) -> mapResponse(rs), args.toArray());

        if (Boolean.TRUE.equals(overdueOnly)) {
            return list.stream().filter(CareTaskResponse::isOverdue).toList();
        }
        return list;
    }

    private String baseSelect() {
        return """
            SELECT
                t.id,
                t.routine_definition_id,
                t.task_category,
                t.scope,
                t.horse_id,
                h.code AS horse_code,
                h.name AS horse_name,
                t.task_type,
                t.title,
                t.description,
                t.scheduled_date,
                t.scheduled_time,
                t.assigned_staff_id,
                t.status,
                t.completed_at,
                t.completed_by_user_id,
                t.completed_by_name,
                t.completion_notes,
                t.photo_url,
                t.created_at
            FROM dbo.care_tasks t
            LEFT JOIN dbo.horses h ON h.id = t.horse_id
            """;
    }

    private CareTaskResponse mapResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID routineDefinitionId = rs.getObject("routine_definition_id", UUID.class);
        String taskCategory = rs.getString("task_category");
        String scope = rs.getString("scope");
        UUID horseId = rs.getObject("horse_id", UUID.class);
        String horseCode = rs.getString("horse_code");
        String horseName = rs.getString("horse_name");
        String taskType = rs.getString("task_type");
        String title = rs.getString("title");
        String description = rs.getString("description");

        java.sql.Date sqlDate = rs.getDate("scheduled_date");
        LocalDate scheduledDate = sqlDate != null ? sqlDate.toLocalDate() : null;

        java.sql.Time sqlTime = rs.getTime("scheduled_time");
        LocalTime scheduledTime = sqlTime != null ? sqlTime.toLocalTime() : null;

        UUID assignedStaffId = rs.getObject("assigned_staff_id", UUID.class);
        String status = rs.getString("status");

        OffsetDateTime completedAt = rs.getObject("completed_at", OffsetDateTime.class);
        UUID completedByUserId = rs.getObject("completed_by_user_id", UUID.class);
        String completedByName = rs.getString("completed_by_name");
        String completionNotes = rs.getString("completion_notes");
        String photoUrl = rs.getString("photo_url");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);

        // Dynamic overdue calculation (NEVER stored as DB status)
        boolean isOverdue = false;
        if ("PENDING".equals(status) && scheduledDate != null && scheduledTime != null) {
            LocalDate today = LocalDate.now(clubZone);
            LocalTime nowTime = LocalTime.now(clubZone);
            isOverdue = scheduledDate.isBefore(today) || (scheduledDate.isEqual(today) && scheduledTime.isBefore(nowTime));
        }

        return new CareTaskResponse(
                id, routineDefinitionId, taskCategory, scope, horseId, horseCode, horseName,
                taskType, title, description, scheduledDate, scheduledTime, assignedStaffId,
                status, isOverdue, completedAt, completedByUserId, completedByName,
                completionNotes, photoUrl, createdAt
        );
    }

    private void validateTask(CreateCareTaskRequest r) {
        if (r.taskType() == null || r.taskType().isBlank()) throw new BusinessException("Loại nhiệm vụ không được để trống");
        if (r.title() == null || r.title().isBlank()) throw new BusinessException("Tiêu đề nhiệm vụ không được để trống");
        if (r.scheduledDate() == null) throw new BusinessException("Ngày lên lịch không được để trống");
        if (r.scheduledTime() == null) throw new BusinessException("Giờ lên lịch không được để trống");
    }
}
