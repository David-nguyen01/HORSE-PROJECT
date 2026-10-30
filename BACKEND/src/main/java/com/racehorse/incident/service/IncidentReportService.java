package com.racehorse.incident.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.incident.dto.CreateIncidentReportRequest;
import com.racehorse.incident.dto.CreateTrainingEmergencyRequest;
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
    public IncidentReportResponse createTrainingEmergency(UUID trainerId, CreateTrainingEmergencyRequest request) {
        record Session(UUID scheduleId, UUID horseId, String horseCode, String horseName, String taskName,
                       UUID groundId, String groundName, UUID laneId, Integer laneNumber,
                       LocalDate trainingDate, UUID caretakerId, UUID vetId) { }
        List<Session> rows = jdbc.query("""
                select s.id schedule_id,h.id horse_id,h.code horse_code,h.name horse_name,s.task_name,
                       r.id ground_id,r.name ground_name,l.id lane_id,l.lane_number,
                       cast(s.scheduled_start at time zone 'SE Asia Standard Time' as date) training_date,
                       h.caretaker_user_id,h.diagnosing_vet_user_id
                from training_schedules s join horses h on h.id=s.horse_id
                left join racetracks r on r.id=s.racetrack_id
                left join trial_run_bookings b on b.training_schedule_id=s.id and b.cancelled_at is null
                left join racetrack_lanes l on l.id=b.racetrack_lane_id
                where s.id=? and h.current_trainer_id=? and h.archived_at is null
                  and s.cancelled_at is null and s.session_status='IN_PROGRESS'
                """, (rs, row) -> new Session(rs.getObject("schedule_id", UUID.class),
                rs.getObject("horse_id", UUID.class), rs.getString("horse_code"), rs.getString("horse_name"),
                rs.getString("task_name"),
                rs.getObject("ground_id", UUID.class), rs.getString("ground_name"), rs.getObject("lane_id", UUID.class),
                (Integer) rs.getObject("lane_number"), rs.getObject("training_date", LocalDate.class),
                rs.getObject("caretaker_user_id", UUID.class), rs.getObject("diagnosing_vet_user_id", UUID.class)),
                request.trainingScheduleId(), trainerId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy buổi tập đang huấn luyện của ngựa do bạn phụ trách");
        Session session = rows.getFirst();
        Map<String, UUID> selected = new LinkedHashMap<>();
        if (request.recipients().contains(CreateTrainingEmergencyRequest.Recipient.CARETAKER)) {
            if (session.caretakerId() == null) throw new BusinessException("Ngựa chưa được phân công nhân viên chuồng phụ trách");
            selected.put("CARETAKER", session.caretakerId());
        }
        if (request.recipients().contains(CreateTrainingEmergencyRequest.Recipient.DIAGNOSING_VET)) {
            if (session.vetId() == null) throw new BusinessException("Ngựa chưa được phân công bác sĩ chẩn đoán");
            selected.put("DIAGNOSING_VET", session.vetId());
        }
        String description = request.description().trim();
        String incidentType = "Khẩn cấp từ huấn luyện viên";
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID incidentId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("incidentType", incidentType);
        data.put("severity", "HIGH");
        data.put("description", description);
        data.put("photoUrl", null);
        data.put("reportedByUserId", trainerId.toString());
        data.put("reportedAt", now.toString());
        data.put("trainingScheduleId", session.scheduleId().toString());
        data.put("trainingTaskName", session.taskName());
        data.put("groundId", session.groundId());
        data.put("groundName", session.groundName());
        data.put("laneId", session.laneId());
        data.put("laneNumber", session.laneNumber());
        data.put("recipients", selected.keySet());
        data.put("resolvedByUserId", null); data.put("resolvedByName", null);
        data.put("resolvedAt", null); data.put("resolutionNotes", null);
        jdbc.update("insert into module_records(id,module_code,record_type,horse_id,user_id,event_date,status,data_json,created_at,updated_at) "
                + "values(?,'STABLE_CARE','INCIDENT_REPORT',?, ?,?,'REPORTED',?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                incidentId, session.horseId(), trainerId, session.trainingDate(), toJson(data));
        int injuredSession = jdbc.update("update training_schedules set session_status='EMERGENCY_STOPPED', "
                        + "updated_at=SYSDATETIMEOFFSET(), version=version+1 where id=? and session_status='IN_PROGRESS'",
                session.scheduleId());
        if (injuredSession != 1)
            throw new BusinessException("Buổi tập đã thay đổi trạng thái; hãy tải lại trước khi gửi khẩn cấp");
        int injuredHorse = jdbc.update("update horses set status='INJURED',health_status='INJURED', "
                        + "health_source_type='TRAINING_EMERGENCY',health_source_id=?,health_changed_by_user_id=?, "
                        + "health_changed_at=SYSDATETIMEOFFSET(),updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                incidentId, trainerId, session.horseId());
        if (injuredHorse != 1)
            throw new BusinessException("Không thể cập nhật trạng thái chấn thương của ngựa");
        Set<UUID> ownerIds = new HashSet<>(jdbc.query(
                "select owner_id from horse_ownerships where horse_id=?",
                (rs, row) -> rs.getObject("owner_id", UUID.class), session.horseId()));
        Set<UUID> recipientIds = new HashSet<>(selected.values());
        recipientIds.addAll(ownerIds);
        // Any available veterinarian may be first to receive an emergency.
        for (UserAccount vet : userRepository.findByRoles(List.of(Role.VETERINARIAN))) {
            recipientIds.add(vet.getId());
        }
        for (UUID recipientId : recipientIds) {
            UserAccount recipient = userRepository.findById(recipientId)
                    .orElseThrow(() -> new BusinessException("Người nhận khẩn cấp không còn hoạt động"));
            notificationService.create(recipient, "TRAINING_EMERGENCY", NotificationPriority.HIGH,
                    "Khẩn cấp trong buổi huấn luyện", session.horseCode() + " - " + description,
                    "INCIDENT_REPORT", incidentId);
        }
        return getReport(incidentId);
    }

    @Transactional(readOnly = true)
    public List<IncidentReportResponse> sentTrainingEmergencies(UUID trainerId) {
        return jdbc.query(baseSelect() + " where m.module_code='STABLE_CARE' and m.record_type='INCIDENT_REPORT' "
                + "and m.user_id=? and JSON_VALUE(m.data_json,'$.trainingScheduleId') is not null "
                + "order by m.created_at desc", (rs, row) -> mapResponse(rs), trainerId);
    }

    @Transactional(readOnly = true)
    public List<IncidentReportResponse> ownerTrainingEmergencies(UUID horseId, UUID ownerId) {
        horseAccess.requireOwned(horseId, ownerId);
        return jdbc.query(baseSelect() + " where m.module_code='STABLE_CARE' and m.record_type='INCIDENT_REPORT' "
                + "and m.horse_id=? and JSON_VALUE(m.data_json,'$.trainingScheduleId') is not null "
                + "order by m.created_at desc", (rs, row) -> mapResponse(rs), horseId);
    }

    @Transactional(readOnly = true)
    public IncidentReportResponse trainingEmergency(UUID incidentId, UUID userId, boolean owner) {
        IncidentReportResponse response = getReport(incidentId);
        if (response.trainingScheduleId() == null) throw new ResourceNotFoundException("Không tìm thấy yêu cầu khẩn cấp");
        if (owner) horseAccess.requireOwned(response.horseId(), userId);
        else if (!userId.equals(response.reportedByUserId()))
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu khẩn cấp của bạn");
        return response;
    }

    @Transactional
    public IncidentReportResponse updateStatus(UUID incidentId, UUID actorId,
            UpdateIncidentStatusRequest request, boolean managerOrVet) {
        IncidentReportResponse existing = getReport(incidentId);
        if (!managerOrVet && !actorId.equals(existing.reportedByUserId())) {
            throw new BusinessException("Chỉ nhân viên gửi báo cáo mới được cập nhật sự cố này");
        }
        String targetStatus = request.status().trim().toUpperCase(Locale.ROOT);
        if ("ACKNOWLEDGED".equals(targetStatus)
                && ("HIGH".equals(existing.severity()) || "CRITICAL".equals(existing.severity()))
                && existing.emergencyVetId() == null) {
            throw new BusinessException("Ca khẩn cấp phải được bác sĩ thú y tiếp nhận qua chức năng Nhận cấp cứu");
        }

        if (!List.of("REPORTED", "UNDER_REVIEW", "ACKNOWLEDGED", "RESOLVED", "DISMISSED").contains(targetStatus)) {
            throw new BusinessException("Trạng thái sự cố không hợp lệ: " + targetStatus);
        }

        String currentJson = jdbc.queryForObject(
                "select data_json from module_records where id=?", String.class, incidentId);
        Map<String, Object> data = readMap(currentJson);
        Object emergencyVet = data.get("emergencyVetId");
        if (emergencyVet != null && !actorId.toString().equalsIgnoreCase(String.valueOf(emergencyVet))) {
            throw new BusinessException("Ca cấp cứu đã được bác sĩ khác tiếp nhận; không thể đổi trạng thái");
        }

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

    @Transactional
    public IncidentReportResponse claimEmergency(UUID incidentId, UUID veterinarianId) {
        IncidentReportResponse report = getReport(incidentId);
        if (!List.of("HIGH", "CRITICAL").contains(report.severity())) {
            throw new BusinessException("Chỉ sự cố mức cao hoặc nguy kịch được tiếp nhận như ca cấp cứu");
        }
        jdbc.queryForObject("select id from horses with (UPDLOCK,HOLDLOCK) where id=?", UUID.class, report.horseId());
        Integer otherClaim = jdbc.queryForObject("""
                select count(*) from module_records where horse_id=? and module_code='STABLE_CARE'
                  and record_type='INCIDENT_REPORT' and status='ACKNOWLEDGED'
                  and JSON_VALUE(data_json,'$.emergencyVetId') is not null
                  and JSON_VALUE(data_json,'$.emergencyExaminationId') is null
                """, Integer.class, report.horseId());
        if (otherClaim != null && otherClaim > 0) {
            throw new BusinessException("Ngựa đang có ca cấp cứu do bác sĩ khác tiếp nhận");
        }
        List<String> names = jdbc.query("select username from users where id=?",
                (rs, row) -> rs.getString(1), veterinarianId);
        if (names.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bác sĩ tiếp nhận");
        int changed = jdbc.update("""
                update module_records set status='ACKNOWLEDGED',
                    data_json=JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(data_json,'$.emergencyVetId',CONVERT(varchar(36),?)),
                                              '$.emergencyVetName',?),
                                              '$.emergencyClaimedAt',CONVERT(varchar(33),SYSDATETIMEOFFSET(),127)),
                    updated_at=SYSDATETIMEOFFSET()
                where id=? and horse_id=? and module_code='STABLE_CARE' and record_type='INCIDENT_REPORT'
                  and status='REPORTED' and JSON_VALUE(data_json,'$.severity') in ('HIGH','CRITICAL')
                """, veterinarianId, names.getFirst(), incidentId, report.horseId());
        if (changed != 1) throw new BusinessException("Ca cấp cứu đã được bác sĩ khác tiếp nhận hoặc không còn mở");
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
                resolutionNotes,
                node.path("trainingScheduleId").isMissingNode() ? null : UUID.fromString(node.path("trainingScheduleId").asText()),
                node.path("trainingTaskName").asText(null), node.path("groundName").asText(null),
                node.path("laneNumber").isNumber() ? node.path("laneNumber").asInt() : null,
                node.path("recipients").isArray() ? java.util.stream.StreamSupport.stream(node.path("recipients").spliterator(), false)
                        .map(JsonNode::asText).toList() : List.of(),
                node.hasNonNull("emergencyVetId") ? UUID.fromString(node.path("emergencyVetId").asText()) : null,
                node.path("emergencyVetName").asText(null),
                node.hasNonNull("emergencyExaminationId") ? UUID.fromString(node.path("emergencyExaminationId").asText()) : null);
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
