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
    private final Clock clock;

    public IncidentReportService(
            JdbcTemplate jdbc,
            HorseAccessService horseAccess,
            UserAccountRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this.jdbc = jdbc;
        this.horseAccess = horseAccess;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
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

        UUID incidentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);

        // Append severity to type or description if needed, or modify schema. 
        // Current schema: incident_reports(id, horse_id, reported_by_user_id, incident_type, description, submitted_at, status, resolved_by, resolved_at, resolution_notes)
        // Note: The original schema didn't have severity explicitly. I will append it to incident_type for now: "[HIGH] Colic"
        String fullIncidentType = "[" + severity + "] " + incidentType;

        jdbc.update(
                "INSERT INTO dbo.incident_reports(id, horse_id, reported_by_user_id, incident_type, description, submitted_at, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'REPORTED')",
                incidentId, horse.getId(), actorId, fullIncidentType, request.description().trim(), now);

        // Handle file if any (we map photoUrl to file link or skip if no stored_files entry)
        // Simplification for FR16: we just assume the file is uploaded.

        // Push immediate notification to Veterinarians and Head Trainers
        dispatchEmergencyNotification(horse, incidentId, incidentType, severity, request.description().trim());

        return getReport(incidentId);
    }

    @Transactional
    public IncidentReportResponse updateStatus(UUID incidentId, UUID actorId, UpdateIncidentStatusRequest request) {
        String targetStatus = request.status().trim().toUpperCase(Locale.ROOT);
        if (!List.of("REPORTED", "UNDER_REVIEW", "RESOLVED").contains(targetStatus)) {
            throw new BusinessException("Trạng thái sự cố không hợp lệ: " + targetStatus);
        }

        if ("RESOLVED".equals(targetStatus)) {
            jdbc.update(
                    "UPDATE dbo.incident_reports SET status=?, resolved_by_user_id=?, resolved_at=?, resolution_notes=? WHERE id=?",
                    targetStatus, actorId, OffsetDateTime.now(clock), request.resolutionNotes() == null ? null : request.resolutionNotes().trim(), incidentId);
        } else {
            jdbc.update("UPDATE dbo.incident_reports SET status=? WHERE id=?", targetStatus, incidentId);
        }

        return getReport(incidentId);
    }

    @Transactional(readOnly = true)
    public IncidentReportResponse getReport(UUID incidentId) {
        List<IncidentReportResponse> rows = jdbc.query(
                baseSelect() + " WHERE i.id=?",
                (rs, rowNum) -> mapResponse(rs), incidentId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy báo cáo sự cố: " + incidentId);
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<IncidentReportResponse> listReports(UUID horseId, String status, String severity, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(baseSelect()).append(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (horseId != null) {
            sql.append(" AND i.horse_id=?");
            args.add(horseId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND i.status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (severity != null && !severity.isBlank()) {
            sql.append(" AND i.incident_type LIKE ?");
            args.add("%[" + severity.trim().toUpperCase(Locale.ROOT) + "]%");
        }
        if (fromDate != null) {
            sql.append(" AND CAST(i.submitted_at AS DATE) >= ?");
            args.add(fromDate);
        }
        if (toDate != null) {
            sql.append(" AND CAST(i.submitted_at AS DATE) <= ?");
            args.add(toDate);
        }

        sql.append(" ORDER BY i.submitted_at DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> mapResponse(rs), args.toArray());
    }

    private void dispatchEmergencyNotification(
            Horse horse, UUID incidentId, String incidentType, String severity, String description) {
        List<UserAccount> recipients = userRepository.findByRoles(List.of(Role.VETERINARIAN, Role.HEAD_TRAINER));
        if (recipients.isEmpty()) return;

        NotificationPriority priority = ("HIGH".equals(severity) || "CRITICAL".equals(severity))
                ? NotificationPriority.HIGH : NotificationPriority.NORMAL;

        String title = String.format("[BÁO CÁO SỰ CỐ - %s] %s (%s)", severity, horse.getName(), horse.getCode());
        String snippet = description.length() > 100 ? description.substring(0, 97) + "..." : description;
        String message = String.format("Loại: %s. Nội dung: %s", incidentType, snippet);

        for (UserAccount recipient : recipients) {
            notificationService.create(
                    recipient, "EMERGENCY_INCIDENT", priority, title, message, "INCIDENT_REPORT", incidentId);
        }
    }

    private String baseSelect() {
        return "SELECT i.id, i.horse_id, h.code AS horse_code, h.name AS horse_name, " +
               "i.incident_type, i.description, i.status, i.submitted_at, " +
               "i.reported_by_user_id, ur.username AS reporter_name, " +
               "i.resolved_by_user_id, res.username AS resolver_name, " +
               "i.resolved_at, i.resolution_notes " +
               "FROM dbo.incident_reports i " +
               "JOIN dbo.horses h ON h.id = i.horse_id " +
               "JOIN dbo.users ur ON ur.id = i.reported_by_user_id " +
               "LEFT JOIN dbo.users res ON res.id = i.resolved_by_user_id";
    }

    private IncidentReportResponse mapResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID horseId = rs.getObject("horse_id", UUID.class);
        String horseCode = rs.getString("horse_code");
        String horseName = rs.getString("horse_name");
        String fullType = rs.getString("incident_type");
        String description = rs.getString("description");
        String status = rs.getString("status");
        OffsetDateTime submittedAt = rs.getObject("submitted_at", OffsetDateTime.class);
        
        UUID reportedById = rs.getObject("reported_by_user_id", UUID.class);
        String reporterName = rs.getString("reporter_name");
        
        UUID resolvedById = rs.getObject("resolved_by_user_id", UUID.class);
        String resolverName = rs.getString("resolver_name");
        OffsetDateTime resolvedAt = rs.getObject("resolved_at", OffsetDateTime.class);
        String resolutionNotes = rs.getString("resolution_notes");

        // Phân tách "[HIGH] Đau bụng"
        String severity = "MEDIUM";
        String incidentType = fullType;
        if (fullType.startsWith("[")) {
            int closeBracket = fullType.indexOf("]");
            if (closeBracket > 0) {
                severity = fullType.substring(1, closeBracket);
                incidentType = fullType.substring(closeBracket + 1).trim();
            }
        }

        return new IncidentReportResponse(
                id, horseId, horseCode, horseName, incidentType, severity, description, null, status,
                submittedAt.toLocalDate(), reportedById, reporterName, submittedAt,
                resolvedById, resolverName, resolvedAt, resolutionNotes);
    }
}
