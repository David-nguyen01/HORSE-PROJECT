package com.racehorse.caretask.service;

import com.racehorse.caretask.dto.*;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CareRoutineDefinitionService {

    private final JdbcTemplate jdbc;

    public CareRoutineDefinitionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<CareRoutineDefinitionResponse> listActive() {
        String sql = """
            SELECT id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order, created_at, updated_at
            FROM dbo.care_routine_definitions
            WHERE is_active = 1
            ORDER BY sort_order ASC, scheduled_time ASC, created_at ASC
            """;
        return jdbc.query(sql, (rs, rn) -> mapRow(rs));
    }

    @Transactional(readOnly = true)
    public List<CareRoutineDefinitionResponse> listReserve() {
        String sql = """
            SELECT id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order, created_at, updated_at
            FROM dbo.care_routine_definitions
            WHERE is_active = 0
            ORDER BY sort_order ASC, scheduled_time ASC, created_at ASC
            """;
        return jdbc.query(sql, (rs, rn) -> mapRow(rs));
    }

    @Transactional(readOnly = true)
    public CareRoutineDefinitionResponse get(UUID id) {
        String sql = """
            SELECT id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order, created_at, updated_at
            FROM dbo.care_routine_definitions
            WHERE id = ?
            """;
        List<CareRoutineDefinitionResponse> list = jdbc.query(sql, (rs, rn) -> mapRow(rs), id);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy định nghĩa quy trình chăm sóc: " + id);
        }
        return list.getFirst();
    }

    @Transactional
    public CareRoutineDefinitionResponse createInReserve(UUID actorId, CreateCareRoutineDefinitionRequest req) {
        if (Boolean.TRUE.equals(req.isActive())) {
            throw new BusinessException("Định nghĩa nhiệm vụ mới chỉ được phép tạo trong Thư viện dự phòng (Reserve Library)");
        }

        String code;
        if (req.code() != null && !req.code().isBlank()) {
            code = req.code().trim().toUpperCase(Locale.ROOT);
            if (code.length() > 50) {
                throw new BusinessException("Mã định nghĩa không được vượt quá 50 ký tự");
            }
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dbo.care_routine_definitions WHERE code = ?",
                    Integer.class, code);
            if (exists != null && exists > 0) {
                throw new BusinessException("Mã quy trình chăm sóc đã tồn tại: " + code);
            }
        } else {
            code = generateUniqueCode(req.title());
        }

        String scope = (req.scope() != null && !req.scope().isBlank())
                ? req.scope().trim().toUpperCase(Locale.ROOT)
                : "FACILITY";

        int sortOrder;
        if (req.sortOrder() != null) {
            sortOrder = req.sortOrder();
        } else {
            Integer max = jdbc.queryForObject(
                    "SELECT MAX(sort_order) FROM dbo.care_routine_definitions WHERE is_active = 0",
                    Integer.class);
            sortOrder = (max != null) ? max + 10 : 0;
        }

        UUID newId = UUID.randomUUID();
        String insertSql = """
            INSERT INTO dbo.care_routine_definitions (
                id, code, title, description, task_type, scheduled_time,
                scope, is_active, sort_order, created_at, updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, 0, ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
            )
            """;

        jdbc.update(
                insertSql,
                newId,
                code,
                req.title().trim(),
                req.description() != null ? req.description().trim() : null,
                req.taskType().trim().toUpperCase(Locale.ROOT),
                java.sql.Time.valueOf(req.scheduledTime()),
                scope,
                sortOrder
        );

        return get(newId);
    }

    @Transactional
    public CareRoutineDefinitionResponse update(UUID id, UUID actorId, UpdateCareRoutineDefinitionRequest req) {
        CareRoutineDefinitionResponse existing = get(id);

        String scope = (req.scope() != null && !req.scope().isBlank())
                ? req.scope().trim().toUpperCase(Locale.ROOT)
                : existing.scope();
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : existing.sortOrder();

        String updateSql = """
            UPDATE dbo.care_routine_definitions
            SET title = ?,
                description = ?,
                task_type = ?,
                scheduled_time = ?,
                scope = ?,
                sort_order = ?,
                updated_at = SYSDATETIMEOFFSET()
            WHERE id = ?
            """;

        int updated = jdbc.update(
                updateSql,
                req.title().trim(),
                req.description() != null ? req.description().trim() : null,
                req.taskType().trim().toUpperCase(Locale.ROOT),
                java.sql.Time.valueOf(req.scheduledTime()),
                scope,
                sortOrder,
                id
        );

        if (updated == 0) {
            throw new ResourceNotFoundException("Không tìm thấy định nghĩa quy trình chăm sóc: " + id);
        }

        return get(id);
    }

    @Transactional
    public CareRoutineDefinitionResponse transfer(UUID id, UUID actorId, boolean targetActive) {
        CareRoutineDefinitionResponse existing = get(id);
        if (existing.isActive() == targetActive) {
            return existing;
        }

        String sql = """
            UPDATE dbo.care_routine_definitions
            SET is_active = ?,
                updated_at = SYSDATETIMEOFFSET()
            WHERE id = ?
            """;

        int updated = jdbc.update(sql, targetActive ? 1 : 0, id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Không tìm thấy định nghĩa quy trình chăm sóc: " + id);
        }

        return get(id);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        // 1. Verify existence
        get(id);

        // 2. Safely unlink historical task instances without deleting or altering their content:
        jdbc.update("UPDATE dbo.care_tasks SET routine_definition_id = NULL WHERE routine_definition_id = ?", id);

        // 3. Physically delete the definition from the catalog:
        int deleted = jdbc.update("DELETE FROM dbo.care_routine_definitions WHERE id = ?", id);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Không tìm thấy định nghĩa quy trình chăm sóc: " + id);
        }
    }

    @Transactional
    public List<CareRoutineDefinitionResponse> reorder(UUID actorId, List<UUID> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new BusinessException("Danh sách ID sắp xếp không được để trống");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID defId = orderedIds.get(i);
            int sortOrder = (i + 1) * 10;
            jdbc.update(
                    "UPDATE dbo.care_routine_definitions SET sort_order = ?, updated_at = SYSDATETIMEOFFSET() WHERE id = ?",
                    sortOrder, defId
            );
        }
        return listActive();
    }

    private String generateUniqueCode(String title) {
        String slug = "TASK";
        if (title != null && !title.isBlank()) {
            String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            if (!normalized.isBlank()) {
                slug = normalized.length() > 28 ? normalized.substring(0, 28) : normalized;
            }
        }
        String candidate = "ROUTINE_" + slug + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        if (candidate.length() > 50) {
            candidate = candidate.substring(0, 50);
        }
        return candidate;
    }

    private CareRoutineDefinitionResponse mapRow(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String code = rs.getString("code");
        String title = rs.getString("title");
        String description = rs.getString("description");
        String taskType = rs.getString("task_type");
        java.sql.Time sqlTime = rs.getTime("scheduled_time");
        LocalTime scheduledTime = sqlTime != null ? sqlTime.toLocalTime() : null;
        String scope = rs.getString("scope");
        boolean isActive = rs.getBoolean("is_active");
        int sortOrder = rs.getInt("sort_order");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);

        return new CareRoutineDefinitionResponse(
                id, code, title, description, taskType, scheduledTime,
                scope, isActive, sortOrder, createdAt, updatedAt
        );
    }
}
