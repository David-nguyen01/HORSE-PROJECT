package com.racehorse.incident.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.incident.dto.CreateIncidentReportRequest;
import com.racehorse.incident.dto.IncidentReportResponse;
import com.racehorse.incident.dto.UpdateIncidentStatusRequest;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class IncidentReportService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccess;
    private final UserAccountRepository userRepository;
    private final NotificationService notificationService;
    private final ObjectMapper json;
    private final Clock clock;

    public IncidentReportService(
            JdbcTemplate jdbc,
            HorseAccessService horseAccess,
            UserAccountRepository userRepository,
            NotificationService notificationService,
            ObjectMapper json,
            Clock clock) {
        this.jdbc = jdbc;
        this.horseAccess = horseAccess;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public IncidentReportResponse createReport(UUID actorId, CreateIncidentReportRequest request) {
        Horse horse = horseAccess.requireActive(request.horseId());

        String incidentType = request.incidentType().trim().toUpperCase(Locale.ROOT);
        String severity = request.severity().trim().toUpperCase(Locale.ROOT);
        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(severity)) {
            throw new BusinessException("Mức độ nghiêm trọng (severity) không hợp lệ: " + severity);
        }

        List<String> names = jdbc.query(
                "select username from users where id=?",
                (rs, rn) -> rs.getString("username"), actorId);
        String reporterName = names.isEmpty() ? "Nhân viên chuồng" : names.getFirst();

        UUID incidentId = UUID.randomUUID();
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("incidentType", incidentType);
        data.put("severity", severity);
        data.put("description", request.description().trim());
        data.put("photoUrl", request.photoUrl() == null || request.photoUrl().isBlank() ? null : request.photoUrl().trim());
        data.put("reportedByUserId", actorId.toString());
        data.put("reportedByName", reporterName);
        data.put("reportedAt", now.toString());
        data.put("resolvedByUserId", null);
        data.put("resolvedByName", null);
        data.put("resolvedAt", null);
        data.put("resolutionNotes", null);

        jdbc.update(
                "insert into module_records(id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at) " +
                        "values (?, 'STABLE_CARE', 'INCIDENT_REPORT', ?, ?, ?, 'REPORTED', ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())",
                incidentId, horse.getId(), actorId, today, toJson(data));

        // Push immediate notification to Veterinarians and Head Trainers
        dispatchEmergencyNotification(horse, incidentId, incidentType, severity, request.description().trim());

        return getReport(incidentId);
    }

    @Transactional
    public IncidentReportResponse updateStatus(UUID incidentId, UUID actorId, UpdateIncidentStatusRequest request) {
        IncidentReportResponse existing = getReport(incidentId);
        String targetStatus = request.status().trim().toUpperCase(Locale.ROOT);

        if (!List.of("REPORTED", "UNDER_REVIEW", "ACKNOWLEDGED", "RESOLVED", "DISMISSED").contains(targetStatus)) {
            throw new BusinessException("Trạng thái sự cố không hợp lệ: " + targetStatus);
        }

        String currentJson = jdbc.queryForObject(
                "select data_json from module_records where id=?", String.class, incidentId);
        Map<String, Object> data = readMap(currentJson);

        List<String> names = jdbc.query(
                "select username from users where id=?",
                (rs, rn) -> rs.getString("username"), actorId);
        String actorName = names.isEmpty() ? "Người xử lý" : names.getFirst();

        if ("RESOLVED".equals(targetStatus) || "DISMISSED".equals(targetStatus)) {
            data.put("resolvedByUserId", actorId.toString());
            data.put("resolvedByName", actorName);
            data.put("resolvedAt", OffsetDateTime.now(clock).toString());
            data.put("resolutionNotes", request.resolutionNotes() == null ? null : request.resolutionNotes().trim());
        } else if ("ACKNOWLEDGED".equals(targetStatus)) {
            data.put("acknowledgedByUserId", actorId.toString());
            data.put("acknowledgedByName", actorName);
            data.put("acknowledgedAt", OffsetDateTime.now(clock).toString());
            data.put("acknowledgementNotes", request.resolutionNotes() == null ? null : request.resolutionNotes().trim());
        }

        jdbc.update(
                "update module_records set status=?, data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                targetStatus, toJson(data), incidentId);

        return getReport(incidentId);
    }

    @Transactional(readOnly = true)
    public IncidentReportResponse getReport(UUID incidentId) {
        List<IncidentReportResponse> rows = jdbc.query(
                baseSelect() + " where m.id=? and m.module_code='STABLE_CARE' and m.record_type='INCIDENT_REPORT'",
                (rs, rowNum) -> mapResponse(rs), incidentId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy báo cáo sự cố: " + incidentId);
        }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<IncidentReportResponse> listReports(UUID horseId, String status, String severity, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(baseSelect())
                .append(" where m.module_code='STABLE_CARE' and m.record_type='INCIDENT_REPORT'");
        List<Object> args = new ArrayList<>();

        if (horseId != null) {
            sql.append(" and m.horse_id=?");
            args.add(horseId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and m.status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (severity != null && !severity.isBlank()) {
            sql.append(" and JSON_VALUE(m.data_json,'$.severity')=?");
            args.add(severity.trim().toUpperCase(Locale.ROOT));
        }
        if (fromDate != null) {
            sql.append(" and m.event_date >= ?");
            args.add(fromDate);
        }
        if (toDate != null) {
            sql.append(" and m.event_date <= ?");
            args.add(toDate);
        }

        sql.append(" order by m.created_at desc");

        return jdbc.query(sql.toString(), (rs, rowNum) -> mapResponse(rs), args.toArray());
    }

    private void dispatchEmergencyNotification(
            Horse horse, UUID incidentId, String incidentType, String severity, String description) {
        List<UserAccount> recipients = userRepository.findByRoles(List.of(Role.VETERINARIAN, Role.HEAD_TRAINER));
        if (recipients.isEmpty()) return;

        NotificationPriority priority = ("HIGH".equals(severity) || "CRITICAL".equals(severity))
                ? NotificationPriority.HIGH
                : NotificationPriority.NORMAL;

        String title = String.format("[BÁO CÁO SỰ CỐ - %s] %s (%s)", severity, horse.getName(), horse.getCode());
        String snippet = description.length() > 100 ? description.substring(0, 97) + "..." : description;
        String message = String.format("Loại: %s. Nội dung: %s", incidentType, snippet);

        for (UserAccount recipient : recipients) {
            notificationService.create(
                    recipient,
                    "EMERGENCY_INCIDENT",
                    priority,
                    title,
                    message,
                    "INCIDENT_REPORT",
                    incidentId);
        }
    }

    private String baseSelect() {
        return "select m.id, m.horse_id, h.code horse_code, h.name horse_name, m.status, " +
                "m.event_date, m.data_json, m.created_at " +
                "from module_records m " +
                "left join horses h on h.id = m.horse_id ";
    }

    private IncidentReportResponse mapResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID horseId = rs.getObject("horse_id", UUID.class);
        String horseCode = rs.getString("horse_code");
        String horseName = rs.getString("horse_name");
        String status = rs.getString("status");
        LocalDate reportDate = rs.getObject("event_date", LocalDate.class);
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);

        JsonNode node = read(rs.getString("data_json"));
        String incidentType = node.path("incidentType").asText("OTHER");
        String severity = node.path("severity").asText("MEDIUM");
        String description = node.path("description").asText("");
        String photoUrl = node.path("photoUrl").isNull() ? null : node.path("photoUrl").asText(null);

        String reportedByStr = node.path("reportedByUserId").isNull() ? null : node.path("reportedByUserId").asText(null);
        UUID reportedByUserId = reportedByStr == null || reportedByStr.isBlank() ? null : UUID.fromString(reportedByStr);
        String reportedByName = node.path("reportedByName").isNull() ? null : node.path("reportedByName").asText(null);
        String reportedAtStr = node.path("reportedAt").isNull() ? null : node.path("reportedAt").asText(null);
        OffsetDateTime reportedAt = reportedAtStr == null || reportedAtStr.isBlank() ? createdAt : OffsetDateTime.parse(reportedAtStr);

        String resolvedByStr = node.path("resolvedByUserId").isNull() ? null : node.path("resolvedByUserId").asText(null);
        UUID resolvedByUserId = resolvedByStr == null || resolvedByStr.isBlank() ? null : UUID.fromString(resolvedByStr);
        String resolvedByName = node.path("resolvedByName").isNull() ? null : node.path("resolvedByName").asText(null);
        String resolvedAtStr = node.path("resolvedAt").isNull() ? null : node.path("resolvedAt").asText(null);
        OffsetDateTime resolvedAt = resolvedAtStr == null || resolvedAtStr.isBlank() ? null : OffsetDateTime.parse(resolvedAtStr);
        String resolutionNotes = node.path("resolutionNotes").isNull() ? null : node.path("resolutionNotes").asText(null);

        return new IncidentReportResponse(
                id,
                horseId,
                horseCode,
                horseName,
                incidentType,
                severity,
                description,
                photoUrl,
                status,
                reportDate,
                reportedByUserId,
                reportedByName,
                reportedAt,
                resolvedByUserId,
                resolvedByName,
                resolvedAt,
                resolutionNotes);
    }

    private JsonNode read(String raw) {
        if (raw == null || raw.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse incident data_json: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> readMap(String raw) {
        if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
        try {
            return json.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JSON map: " + e.getMessage(), e);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize to JSON: " + e.getMessage(), e);
        }
    }
}
