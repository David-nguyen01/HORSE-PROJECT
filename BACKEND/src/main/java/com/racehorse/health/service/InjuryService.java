package com.racehorse.health.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.*;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class InjuryService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccessService;
    private final ObjectMapper json;

    public InjuryService(
            JdbcTemplate jdbc,
            HorseAccessService horseAccessService,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseAccessService = horseAccessService;
        this.json = json;
    }

    @Transactional
    public InjuryResponse createInjury(
            UUID horseId,
            UUID veterinarianId,
            CreateInjuryMarkerRequest req) {
        horseAccessService.requireActive(horseId);

        String severity = normalizeSeverity(req.severity());
        String initialStatus = normalizeRecoveryStatus(req.initialStatus() != null ? req.initialStatus() : "ACUTE");

        UUID injuryId = UUID.randomUUID();
        List<String> fileIds = req.attachmentFileIds() != null
                ? req.attachmentFileIds().stream().map(UUID::toString).toList()
                : List.of();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("examinationId", req.examinationId() != null ? req.examinationId().toString() : null);
        data.put("bodyLocation", req.bodyLocation().trim());
        data.put("x", req.x());
        data.put("y", req.y());
        data.put("z", req.z());
        data.put("severity", severity);
        data.put("currentStatus", initialStatus);
        data.put("notes", trimToNull(req.notes()));
        data.put("attachmentFileIds", fileIds);

        LocalDate today = LocalDate.now();
        jdbc.update("""
                insert into module_records(
                    id, module_code, record_type, source_table,
                    horse_id, user_id, status, event_date, data_json,
                    created_at, updated_at
                ) values (?, 'MEDICAL', 'INJURY_MARKER', 'injury_markers',
                          ?, ?, ?, ?, ?,
                          SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())
                """,
                injuryId, horseId, veterinarianId, initialStatus, today, toJson(data));

        // Create initial update entry for chronological recovery timeline
        UUID updateId = UUID.randomUUID();
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("injuryMarkerId", injuryId.toString());
        updateData.put("recoveryStatus", initialStatus);
        updateData.put("notes", trimToNull(req.notes()));
        updateData.put("attachmentFileIds", fileIds);

        jdbc.update("""
                insert into module_records(
                    id, module_code, record_type, source_table,
                    horse_id, user_id, status, event_date, data_json,
                    created_at, updated_at
                ) values (?, 'MEDICAL', 'INJURY_RECOVERY_UPDATE', 'injury_recovery_updates',
                          ?, ?, ?, ?, ?,
                          SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())
                """,
                updateId, horseId, veterinarianId, initialStatus, today, toJson(updateData));

        String vetName = findUsername(veterinarianId);
        OffsetDateTime now = OffsetDateTime.now();

        return new InjuryResponse(
                injuryId,
                horseId,
                req.examinationId(),
                req.bodyLocation().trim(),
                req.x(),
                req.y(),
                req.z(),
                severity,
                initialStatus,
                trimToNull(req.notes()),
                req.attachmentFileIds() != null ? req.attachmentFileIds() : List.of(),
                veterinarianId,
                vetName,
                now,
                now,
                1);
    }

    @Transactional
    public InjuryRecoveryUpdateResponse addRecoveryUpdate(
            UUID injuryId,
            UUID veterinarianId,
            AddInjuryRecoveryUpdateRequest req) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, horse_id, data_json
                from module_records
                where id = ? and module_code = 'MEDICAL' and record_type = 'INJURY_MARKER'
                """, injuryId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy dấu chấn thương: " + injuryId);
        }

        UUID horseId = toUuid(rows.getFirst().get("horse_id"));
        horseAccessService.requireActive(horseId);

        String newStatus = normalizeRecoveryStatus(req.recoveryStatus());

        UUID updateId = UUID.randomUUID();
        List<String> fileIds = req.attachmentFileIds() != null
                ? req.attachmentFileIds().stream().map(UUID::toString).toList()
                : List.of();

        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("injuryMarkerId", injuryId.toString());
        updateData.put("recoveryStatus", newStatus);
        updateData.put("notes", trimToNull(req.notes()));
        updateData.put("attachmentFileIds", fileIds);

        LocalDate today = LocalDate.now();
        jdbc.update("""
                insert into module_records(
                    id, module_code, record_type, source_table,
                    horse_id, user_id, status, event_date, data_json,
                    created_at, updated_at
                ) values (?, 'MEDICAL', 'INJURY_RECOVERY_UPDATE', 'injury_recovery_updates',
                          ?, ?, ?, ?, ?,
                          SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())
                """,
                updateId, horseId, veterinarianId, newStatus, today, toJson(updateData));

        // Update marker status & currentStatus in JSON
        jdbc.update("""
                update module_records
                set status = ?,
                    data_json = JSON_MODIFY(data_json, '$.currentStatus', ?),
                    updated_at = SYSDATETIMEOFFSET()
                where id = ?
                """,
                newStatus, newStatus, injuryId);

        String vetName = findUsername(veterinarianId);
        OffsetDateTime now = OffsetDateTime.now();

        return new InjuryRecoveryUpdateResponse(
                updateId,
                injuryId,
                newStatus,
                trimToNull(req.notes()),
                req.attachmentFileIds() != null ? req.attachmentFileIds() : List.of(),
                veterinarianId,
                vetName,
                now);
    }

    @Transactional(readOnly = true)
    public InjuryDetailResponse getDetail(UUID injuryId, UUID userId, boolean privileged) {
        List<InjuryDetailResponse> rows = jdbc.query("""
                select m.id, m.horse_id, h.code as horse_code, h.name as horse_name,
                       m.user_id, u.username, m.created_at, m.updated_at, m.data_json
                from module_records m
                join horses h on h.id = m.horse_id
                join users u on u.id = m.user_id
                where m.id = ? and m.module_code = 'MEDICAL' and m.record_type = 'INJURY_MARKER'
                """, (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID horseId = rs.getObject("horse_id", UUID.class);
            String horseCode = rs.getString("horse_code");
            String horseName = rs.getString("horse_name");
            UUID vetId = rs.getObject("user_id", UUID.class);
            String vetName = rs.getString("username");
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
            JsonNode node = read(rs.getString("data_json"));

            String examIdStr = node.path("examinationId").isNull() ? null : node.path("examinationId").asText(null);
            UUID examId = examIdStr != null && !examIdStr.isBlank() ? UUID.fromString(examIdStr) : null;
            String bodyLocation = node.path("bodyLocation").asText("");
            Double x = node.path("x").asDouble();
            Double y = node.path("y").asDouble();
            Double z = node.path("z").asDouble();
            String severity = node.path("severity").asText("");
            String currentStatus = node.path("currentStatus").asText("");
            String notes = node.path("notes").isNull() ? null : node.path("notes").asText(null);

            List<UUID> attachmentFileIds = parseUuids(node.path("attachmentFileIds"));

            return new InjuryDetailResponse(
                    id, horseId, horseCode, horseName, examId, bodyLocation,
                    x, y, z, severity, currentStatus, notes, attachmentFileIds,
                    vetId, vetName, createdAt, updatedAt, List.of());
        }, injuryId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy dấu chấn thương: " + injuryId);
        }

        InjuryDetailResponse detail = rows.getFirst();
        horseAccessService.requireReadable(detail.horseId(), userId, privileged);

        // Fetch recovery timeline updates
        List<InjuryDetailResponse.RecoveryUpdateView> timeline = jdbc.query("""
                select u.id, u.user_id, usr.username, u.created_at, u.data_json
                from module_records u
                join users usr on usr.id = u.user_id
                where u.module_code = 'MEDICAL' and u.record_type = 'INJURY_RECOVERY_UPDATE'
                  and JSON_VALUE(u.data_json, '$.injuryMarkerId') = ?
                order by u.created_at asc
                """, (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID authorId = rs.getObject("user_id", UUID.class);
            String authorName = rs.getString("username");
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            JsonNode node = read(rs.getString("data_json"));

            String status = node.path("recoveryStatus").asText("");
            String notes = node.path("notes").isNull() ? null : node.path("notes").asText(null);
            List<UUID> attachments = parseUuids(node.path("attachmentFileIds"));

            return new InjuryDetailResponse.RecoveryUpdateView(
                    id, injuryId, status, notes, attachments, authorId, authorName, createdAt);
        }, injuryId.toString());

        return new InjuryDetailResponse(
                detail.id(), detail.horseId(), detail.horseCode(), detail.horseName(),
                detail.examinationId(), detail.bodyLocation(),
                detail.x(), detail.y(), detail.z(),
                detail.severity(), detail.currentStatus(), detail.initialNotes(),
                detail.attachmentFileIds(), detail.recordedByUserId(),
                detail.recordedByName(), detail.createdAt(), detail.updatedAt(),
                timeline);
    }

    @Transactional(readOnly = true)
    public List<InjuryResponse> listByHorse(
            UUID horseId,
            UUID userId,
            boolean privileged,
            String statusFilter) {
        horseAccessService.requireReadable(horseId, userId, privileged);

        String normalizedStatus = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            normalizedStatus = normalizeRecoveryStatus(statusFilter);
        }

        String sql = """
                select m.id, m.horse_id, m.user_id, usr.username,
                       m.created_at, m.updated_at, m.data_json,
                       (select count(*)
                        from module_records u
                        where u.module_code = 'MEDICAL' and u.record_type = 'INJURY_RECOVERY_UPDATE'
                          and JSON_VALUE(u.data_json, '$.injuryMarkerId') = cast(m.id as varchar(36))
                       ) as update_count
                from module_records m
                join users usr on usr.id = m.user_id
                where m.module_code = 'MEDICAL' and m.record_type = 'INJURY_MARKER'
                  and m.horse_id = ?
                """
                + (normalizedStatus != null ? " and JSON_VALUE(m.data_json, '$.currentStatus') = ? " : "")
                + " order by m.created_at desc";

        List<Object> args = new ArrayList<>();
        args.add(horseId);
        if (normalizedStatus != null) {
            args.add(normalizedStatus);
        }

        return jdbc.query(sql, (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID vetId = rs.getObject("user_id", UUID.class);
            String vetName = rs.getString("username");
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
            int updateCount = rs.getInt("update_count");
            JsonNode node = read(rs.getString("data_json"));

            String examIdStr = node.path("examinationId").isNull() ? null : node.path("examinationId").asText(null);
            UUID examId = examIdStr != null && !examIdStr.isBlank() ? UUID.fromString(examIdStr) : null;
            String bodyLocation = node.path("bodyLocation").asText("");
            Double x = node.path("x").asDouble();
            Double y = node.path("y").asDouble();
            Double z = node.path("z").asDouble();
            String severity = node.path("severity").asText("");
            String currentStatus = node.path("currentStatus").asText("");
            String notes = node.path("notes").isNull() ? null : node.path("notes").asText(null);
            List<UUID> attachmentFileIds = parseUuids(node.path("attachmentFileIds"));

            return new InjuryResponse(
                    id, horseId, examId, bodyLocation,
                    x, y, z, severity, currentStatus, notes,
                    attachmentFileIds, vetId, vetName,
                    createdAt, updatedAt, updateCount);
        }, args.toArray());
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            throw new BusinessException("Mức độ nghiêm trọng không được để trống");
        }
        String normalized = severity.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MILD", "MODERATE", "SEVERE").contains(normalized)) {
            throw new BusinessException("Mức độ nghiêm trọng chỉ nhận MILD, MODERATE hoặc SEVERE");
        }
        return normalized;
    }

    private String normalizeRecoveryStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException("Trạng thái hồi phục không được để trống");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACUTE", "RECOVERING", "HEALED").contains(normalized)) {
            throw new BusinessException("Trạng thái hồi phục chỉ nhận ACUTE, RECOVERING hoặc HEALED");
        }
        return normalized;
    }

    private List<UUID> parseUuids(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<UUID> list = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) {
                list.add(UUID.fromString(text));
            }
        }
        return list;
    }

    @Transactional
    public void delete(UUID injuryId, UUID actorId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from module_records where id=? and module_code='MEDICAL' and record_type='INJURY'",
                Integer.class, injuryId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin chấn thương: " + injuryId);
        }
        // Delete recovery updates of this injury
        jdbc.update("delete from module_records where module_code='MEDICAL' and record_type='INJURY_RECOVERY_UPDATE' and JSON_VALUE(data_json, '$.injuryId')=?", injuryId.toString());
        // Delete injury record
        jdbc.update("delete from module_records where id=? and module_code='MEDICAL' and record_type='INJURY'", injuryId);
    }

    private String findUsername(UUID userId) {
        List<String> names = jdbc.query("select username from users where id = ?",
                (rs, rowNum) -> rs.getString(1), userId);
        return names.isEmpty() ? "Unknown" : names.getFirst();
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dữ liệu JSON chấn thương không hợp lệ", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Không thể serialize JSON chấn thương", exception);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID toUuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
