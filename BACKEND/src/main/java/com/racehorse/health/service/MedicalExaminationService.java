package com.racehorse.health.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.*;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MedicalExaminationService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccessService;
    private final TrainingLockRepository trainingLockRepository;
    private final TrainingLockService trainingLockService;
    private final PreventiveCareService preventiveCareService;
    private final UserAccountRepository userRepository;
    private final NotificationService notificationService;
    private final ObjectMapper json;

    @org.springframework.beans.factory.annotation.Autowired
    public MedicalExaminationService(
            JdbcTemplate jdbc,
            HorseAccessService horseAccessService,
            TrainingLockRepository trainingLockRepository,
            TrainingLockService trainingLockService,
            PreventiveCareService preventiveCareService,
            UserAccountRepository userRepository,
            NotificationService notificationService,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseAccessService = horseAccessService;
        this.trainingLockRepository = trainingLockRepository;
        this.trainingLockService = trainingLockService;
        this.preventiveCareService = preventiveCareService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.json = json;
    }

    public MedicalExaminationService(
            JdbcTemplate jdbc,
            HorseAccessService horseAccessService,
            ObjectMapper json) {
        this(jdbc, horseAccessService, null, null, null, null, null, json);
    }

    @Transactional
    public MedicalExaminationResponse create(
            UUID horseId,
            UUID veterinarianId,
            CreateMedicalExaminationRequest req) {
        Horse horse = horseAccessService.requireActive(horseId);
        requireInitialExamAccess(horseId, veterinarianId, horse.getIntakeStatus());

        LocalDate today = LocalDate.now();
        if (req.examinationDate().isAfter(today)) {
            throw new BusinessException("Ngày khám không được ở tương lai");
        }
        if (req.followUpDate() != null && req.followUpDate().isBefore(req.examinationDate())) {
            throw new BusinessException("Ngày tái khám phải sau hoặc bằng ngày khám");
        }

        String healthStatusAfter = normalizeHealthStatus(req.healthStatusAfter());

        UUID examId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("symptoms", req.symptoms().trim());
        data.put("diagnosis", req.diagnosis().trim());
        data.put("treatmentProtocol", req.treatmentProtocol().trim());
        data.put("medicationNotes", trimToNull(req.medicationNotes()));
        data.put("followUpDate", req.followUpDate() != null ? req.followUpDate().toString() : null);
        data.put("healthStatusAfter", healthStatusAfter);
        if (req.heartRateBpm() != null) data.put("heartRateBpm", req.heartRateBpm());
        if (req.bodyLengthCm() != null) data.put("bodyLengthCm", req.bodyLengthCm());
        if (req.temperatureCelsius() != null) data.put("temperatureCelsius", req.temperatureCelsius());
        if (req.heightCm() != null) data.put("heightCm", req.heightCm());
        if (req.weightKg() != null) {
            data.put("weightKg", req.weightKg());
            jdbc.update("update horses set weight_kg=?, weight_record_id=?, weight_measured_at=SYSDATETIMEOFFSET(), weight_measured_by_user_id=?, weight_notes=?, updated_at=SYSDATETIMEOFFSET(), version=version+1 where id=?",
                    req.weightKg(), examId, veterinarianId, "Ghi nhận lúc khám lâm sàng: " + req.diagnosis().trim(), horseId);
        }

        jdbc.update("""
                insert into module_records(
                    id, module_code, record_type, source_table,
                    horse_id, user_id, status, event_date, data_json,
                    created_at, updated_at
                ) values (?, 'MEDICAL', 'MEDICAL_EXAMINATION', 'medical_examinations',
                          ?, ?, 'RECORDED', ?, ?,
                          SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())
                """,
                examId, horseId, veterinarianId, req.examinationDate(), toJson(data));

        if (healthStatusAfter != null) {
            jdbc.update("""
                    update horses
                    set health_status = ?,
                        health_changed_by_user_id = ?,
                        health_source_type = 'MEDICAL_EXAM',
                        health_source_id = ?,
                        health_changed_at = SYSDATETIMEOFFSET(),
                        updated_at = SYSDATETIMEOFFSET(),
                        version = version + 1
                    where id = ?
                    """,
                    healthStatusAfter, veterinarianId, examId, horseId);
        }

        // Tự động tạo Training Lock nếu chẩn đoán liên quan đến chấn thương
        boolean isInjury = isInjuryDiagnosis(req.diagnosis(), healthStatusAfter);
        if (isInjury && trainingLockService != null && trainingLockRepository != null) {
            if (trainingLockRepository.findByHorseIdAndReleasedAtIsNull(horseId).isEmpty()) {
                String lockReason = "Khóa tự động từ kết quả khám: " + req.diagnosis().trim();
                trainingLockService.lock(horseId, veterinarianId,
                        new CreateTrainingLockRequest(lockReason, req.followUpDate()));
            }
            if (userRepository != null && notificationService != null) {
                List<UserAccount> headTrainers = userRepository.findByRoles(List.of(Role.HEAD_TRAINER));
                for (UserAccount ht : headTrainers) {
                    notificationService.create(
                            ht,
                            "TRAINING_LOCK_AUTOMATIC",
                            NotificationPriority.HIGH,
                            "Khóa lịch tập y tế: " + horse.getCode() + " - " + horse.getName(),
                            "Ngựa " + horse.getCode() + " bị khóa lịch tập do kết quả chẩn đoán: " + req.diagnosis().trim(),
                            "HORSE",
                            horseId);
                }
            }
        }

        // Tích hợp tạo lịch chăm sóc phòng ngừa nếu có
        List<CreatePreventiveCareRequest> schedules = new ArrayList<>();
        if (req.preventiveSchedules() != null && !req.preventiveSchedules().isEmpty()) {
            schedules.addAll(req.preventiveSchedules());
        }
        if (req.preventiveCareSchedule() != null) {
            schedules.add(req.preventiveCareSchedule());
        }

        List<UUID> createdScheduleIds = new ArrayList<>();
        if (!schedules.isEmpty() && preventiveCareService != null) {
            List<UserAccount> staffList = userRepository != null ? userRepository.findByRoles(List.of(Role.STABLE_STAFF)) : List.of();
            for (CreatePreventiveCareRequest schedule : schedules) {
                if (schedule == null || schedule.dueDate() == null) continue;
                PreventiveCareResponse care = preventiveCareService.create(horseId, veterinarianId, schedule);
                if (care != null && care.id() != null) {
                    createdScheduleIds.add(care.id());
                }
                if (notificationService != null && !staffList.isEmpty()) {
                    for (UserAccount staff : staffList) {
                        notificationService.create(
                                staff,
                                "PREVENTIVE_CARE_SCHEDULED",
                                NotificationPriority.NORMAL,
                                "Lịch chăm sóc phòng ngừa: " + horse.getCode() + " - " + horse.getName(),
                                "Bác sĩ đã lên lịch " + schedule.careType() + " cho ngựa " + horse.getName() + " vào ngày " + schedule.dueDate(),
                                "HORSE",
                                horseId);
                    }
                }
            }
        }

        if (!createdScheduleIds.isEmpty()) {
            data.put("preventiveScheduleIds", createdScheduleIds.stream().map(UUID::toString).toList());
            jdbc.update("update module_records set data_json = ? where id = ?", toJson(data), examId);
        }

        String vetName = findUsername(veterinarianId);
        OffsetDateTime createdAt = OffsetDateTime.now();

        return new MedicalExaminationResponse(
                examId,
                horseId,
                veterinarianId,
                vetName,
                req.examinationDate(),
                req.symptoms().trim(),
                req.diagnosis().trim(),
                req.treatmentProtocol().trim(),
                trimToNull(req.medicationNotes()),
                req.followUpDate(),
                healthStatusAfter,
                createdAt,
                0,
                req.heartRateBpm(),
                req.bodyLengthCm(),
                req.temperatureCelsius(),
                req.heightCm(),
                req.weightKg());
    }

    @Transactional
    public MedicalExaminationAddendumResponse addAddendum(
            UUID examId,
            UUID authorId,
            AddMedicalExaminationAddendumRequest req) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, horse_id
                from module_records
                where id = ? and module_code = 'MEDICAL' and record_type = 'MEDICAL_EXAMINATION'
                """, examId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ khám bệnh: " + examId);
        }

        UUID horseId = toUuid(rows.getFirst().get("horse_id"));
        Horse horse = horseAccessService.requireActive(horseId);
        requireInitialExamAccess(horseId, authorId, horse.getIntakeStatus());

        String healthStatusAfter = normalizeHealthStatus(req.healthStatusAfter());

        UUID addendumId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("examinationId", examId.toString());
        data.put("notes", req.notes().trim());
        data.put("healthStatusAfter", healthStatusAfter);

        LocalDate today = LocalDate.now();
        jdbc.update("""
                insert into module_records(
                    id, module_code, record_type, source_table,
                    horse_id, user_id, status, event_date, data_json,
                    created_at, updated_at
                ) values (?, 'MEDICAL', 'MEDICAL_EXAMINATION_ADDENDUM', 'medical_examination_addenda',
                          ?, ?, 'RECORDED', ?, ?,
                          SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())
                """,
                addendumId, horseId, authorId, today, toJson(data));

        if (healthStatusAfter != null) {
            jdbc.update("""
                    update horses
                    set health_status = ?,
                        health_changed_by_user_id = ?,
                        health_source_type = 'MEDICAL_EXAM_ADDENDUM',
                        health_source_id = ?,
                        health_changed_at = SYSDATETIMEOFFSET(),
                        updated_at = SYSDATETIMEOFFSET(),
                        version = version + 1
                    where id = ?
                    """,
                    healthStatusAfter, authorId, addendumId, horseId);
        }

        String authorName = findUsername(authorId);
        OffsetDateTime createdAt = OffsetDateTime.now();

        return new MedicalExaminationAddendumResponse(
                addendumId,
                examId,
                authorId,
                authorName,
                req.notes().trim(),
                healthStatusAfter,
                createdAt);
    }

    @Transactional(readOnly = true)
    public MedicalExaminationDetailResponse getDetail(UUID examId, UUID userId, boolean privileged) {
        List<MedicalExaminationDetailResponse> rows = jdbc.query("""
                select m.id, m.horse_id, h.code as horse_code, h.name as horse_name,
                       m.user_id as vet_id, u.username as vet_name,
                       m.event_date, m.created_at, m.data_json
                from module_records m
                join horses h on h.id = m.horse_id
                join users u on u.id = m.user_id
                where m.id = ? and m.module_code = 'MEDICAL' and m.record_type = 'MEDICAL_EXAMINATION'
                """, (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID horseId = rs.getObject("horse_id", UUID.class);
            String horseCode = rs.getString("horse_code");
            String horseName = rs.getString("horse_name");
            UUID vetId = rs.getObject("vet_id", UUID.class);
            String vetName = rs.getString("vet_name");
            LocalDate examDate = rs.getObject("event_date", LocalDate.class);
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            JsonNode node = read(rs.getString("data_json"));

            String symptoms = node.path("symptoms").asText("");
            String diagnosis = node.path("diagnosis").asText("");
            String treatment = node.path("treatmentProtocol").asText("");
            String medication = node.path("medicationNotes").isNull() ? null : node.path("medicationNotes").asText(null);
            String followUpStr = node.path("followUpDate").isNull() ? null : node.path("followUpDate").asText(null);
            LocalDate followUpDate = followUpStr != null && !followUpStr.isBlank() ? LocalDate.parse(followUpStr) : null;
            String healthStatusAfter = node.path("healthStatusAfter").isNull() ? null : node.path("healthStatusAfter").asText(null);
            List<UUID> scheduleIds = new ArrayList<>();
            if (node.has("preventiveScheduleIds") && node.path("preventiveScheduleIds").isArray()) {
                for (JsonNode sid : node.path("preventiveScheduleIds")) {
                    try {
                        scheduleIds.add(UUID.fromString(sid.asText()));
                    } catch (Exception ignored) {}
                }
            }

            Integer heartRateBpm = node.hasNonNull("heartRateBpm") ? node.path("heartRateBpm").asInt() : null;
            Integer bodyLengthCm = node.hasNonNull("bodyLengthCm") ? node.path("bodyLengthCm").asInt() : null;
            BigDecimal temperatureCelsius = node.hasNonNull("temperatureCelsius") ? new BigDecimal(node.path("temperatureCelsius").asText()) : null;
            Integer heightCm = node.hasNonNull("heightCm") ? node.path("heightCm").asInt() : null;
            BigDecimal weightKg = node.hasNonNull("weightKg") ? new BigDecimal(node.path("weightKg").asText()) : null;

            return new MedicalExaminationDetailResponse(
                    id, horseId, horseCode, horseName, vetId, vetName,
                    examDate, symptoms, diagnosis, treatment, medication,
                    followUpDate, healthStatusAfter, createdAt, List.of(), scheduleIds,
                    heartRateBpm, bodyLengthCm, temperatureCelsius, heightCm, weightKg);
        }, examId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ khám bệnh: " + examId);
        }

        MedicalExaminationDetailResponse exam = rows.getFirst();
        horseAccessService.requireReadable(exam.horseId(), userId, privileged);

        // Fetch addenda
        List<MedicalExaminationDetailResponse.AddendumView> addenda = jdbc.query("""
                select a.id, a.user_id, u.username as author_name, a.created_at, a.data_json
                from module_records a
                join users u on u.id = a.user_id
                where a.module_code = 'MEDICAL' and a.record_type = 'MEDICAL_EXAMINATION_ADDENDUM'
                  and JSON_VALUE(a.data_json, '$.examinationId') = ?
                order by a.created_at asc
                """, (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID authorId = rs.getObject("user_id", UUID.class);
            String authorName = rs.getString("author_name");
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            JsonNode node = read(rs.getString("data_json"));
            String notes = node.path("notes").asText("");
            String healthStatus = node.path("healthStatusAfter").isNull() ? null : node.path("healthStatusAfter").asText(null);
            return new MedicalExaminationDetailResponse.AddendumView(
                    id, authorId, authorName, notes, healthStatus, createdAt);
        }, examId.toString());

        return new MedicalExaminationDetailResponse(
                exam.id(), exam.horseId(), exam.horseCode(), exam.horseName(),
                exam.veterinarianId(), exam.veterinarianName(),
                exam.examinationDate(), exam.symptoms(), exam.diagnosis(),
                exam.treatmentProtocol(), exam.medicationNotes(),
                exam.followUpDate(), exam.healthStatusAfter(),
                exam.createdAt(), addenda, exam.preventiveScheduleIds(),
                exam.heartRateBpm(), exam.bodyLengthCm(), exam.temperatureCelsius(),
                exam.heightCm(), exam.weightKg());
    }

    @Transactional(readOnly = true)
    public List<MedicalExaminationResponse> listByHorse(UUID horseId, UUID userId, boolean privileged) {
        horseAccessService.requireReadable(horseId, userId, privileged);

        return jdbc.query("""
                select m.id, m.horse_id, m.user_id as vet_id, u.username as vet_name,
                       m.event_date, m.created_at, m.data_json,
                       (select count(*)
                        from module_records a
                        where a.module_code = 'MEDICAL' and a.record_type = 'MEDICAL_EXAMINATION_ADDENDUM'
                          and JSON_VALUE(a.data_json, '$.examinationId') = cast(m.id as varchar(36))
                       ) as addenda_count
                from module_records m
                join users u on u.id = m.user_id
                where m.module_code = 'MEDICAL' and m.record_type = 'MEDICAL_EXAMINATION'
                  and m.horse_id = ?
                order by m.event_date desc, m.created_at desc
                """, (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID vetId = rs.getObject("vet_id", UUID.class);
            String vetName = rs.getString("vet_name");
            LocalDate examDate = rs.getObject("event_date", LocalDate.class);
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            int addendaCount = rs.getInt("addenda_count");
            JsonNode node = read(rs.getString("data_json"));

            String symptoms = node.path("symptoms").asText("");
            String diagnosis = node.path("diagnosis").asText("");
            String treatment = node.path("treatmentProtocol").asText("");
            String medication = node.path("medicationNotes").isNull() ? null : node.path("medicationNotes").asText(null);
            String followUpStr = node.path("followUpDate").isNull() ? null : node.path("followUpDate").asText(null);
            LocalDate followUpDate = followUpStr != null && !followUpStr.isBlank() ? LocalDate.parse(followUpStr) : null;
            String healthStatusAfter = node.path("healthStatusAfter").isNull() ? null : node.path("healthStatusAfter").asText(null);
            Integer heartRateBpm = node.hasNonNull("heartRateBpm") ? node.path("heartRateBpm").asInt() : null;
            Integer bodyLengthCm = node.hasNonNull("bodyLengthCm") ? node.path("bodyLengthCm").asInt() : null;
            BigDecimal temperatureCelsius = node.hasNonNull("temperatureCelsius") ? new BigDecimal(node.path("temperatureCelsius").asText()) : null;
            Integer heightCm = node.hasNonNull("heightCm") ? node.path("heightCm").asInt() : null;
            BigDecimal weightKg = node.hasNonNull("weightKg") ? new BigDecimal(node.path("weightKg").asText()) : null;

            return new MedicalExaminationResponse(
                    id, horseId, vetId, vetName,
                    examDate, symptoms, diagnosis, treatment, medication,
                    followUpDate, healthStatusAfter, createdAt, addendaCount,
                    heartRateBpm, bodyLengthCm, temperatureCelsius, heightCm, weightKg);
        }, horseId);
    }

    private String normalizeHealthStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("HEALTHY".equals(normalized) || "ACTIVE".equals(normalized)) normalized = "FIT";
        else if ("MONITORING".equals(normalized)) normalized = "MONITOR";
        else if ("UNDER_TREATMENT".equals(normalized)) normalized = "INJURED";
        if (!Set.of("FIT", "MONITOR", "INJURED", "QUARANTINED").contains(normalized)) {
            throw new BusinessException("Trạng thái sức khỏe chỉ nhận FIT, MONITOR, INJURED hoặc QUARANTINED");
        }
        return normalized;
    }

    @Transactional
    public void delete(UUID examId, UUID actorId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select horse_id from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'",
                examId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ khám bệnh: " + examId);
        }
        UUID horseId = toUuid(rows.getFirst().get("horse_id"));
        if (horseId != null) {
            horseAccessService.requireActive(horseId);
        }
        // Delete addenda of this examination
        jdbc.update("delete from module_records where module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION_ADDENDUM' and JSON_VALUE(data_json, '$.examinationId')=?", examId.toString());
        // Delete examination record
        jdbc.update("delete from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'", examId);

        // Revert horse health status if this exam was the source
        if (horseId != null) {
            revertHorseHealthStatusIfSourceMatches(horseId, examId, actorId);
        }
    }

    @Transactional
    public void deleteAddendum(UUID addendumId, UUID actorId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select horse_id from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION_ADDENDUM'",
                addendumId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy phụ lục bệnh án: " + addendumId);
        }
        UUID horseId = toUuid(rows.getFirst().get("horse_id"));
        if (horseId != null) {
            horseAccessService.requireActive(horseId);
        }
        jdbc.update("delete from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION_ADDENDUM'", addendumId);

        if (horseId != null) {
            revertHorseHealthStatusIfSourceMatches(horseId, addendumId, actorId);
        }
    }

    private void revertHorseHealthStatusIfSourceMatches(UUID horseId, UUID deletedSourceId, UUID actorId) {
        List<Map<String, Object>> horseRows = jdbc.queryForList(
                "select health_source_id from horses where id=?", horseId);
        if (horseRows.isEmpty()) return;
        UUID currentSourceId = toUuid(horseRows.getFirst().get("health_source_id"));
        if (currentSourceId == null || !currentSourceId.equals(deletedSourceId)) {
            return;
        }

        List<Map<String, Object>> remainingExams = jdbc.queryForList("""
                select top 1 id, data_json from module_records
                where horse_id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'
                order by event_date desc, created_at desc
                """, horseId);

        if (!remainingExams.isEmpty()) {
            UUID prevExamId = toUuid(remainingExams.getFirst().get("id"));
            JsonNode examData = read((String) remainingExams.getFirst().get("data_json"));
            String prevStatus = examData.path("healthStatusAfter").asText("FIT");

            List<Map<String, Object>> addenda = jdbc.queryForList("""
                    select top 1 id, data_json from module_records
                    where module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION_ADDENDUM'
                      and JSON_VALUE(data_json, '$.examinationId')=?
                    order by created_at desc
                    """, prevExamId.toString());
            if (!addenda.isEmpty()) {
                JsonNode addendumData = read((String) addenda.getFirst().get("data_json"));
                String addendumStatus = addendumData.path("healthStatusAfter").asText(null);
                if (addendumStatus != null && !addendumStatus.isBlank()) {
                    prevStatus = addendumStatus;
                    prevExamId = toUuid(addenda.getFirst().get("id"));
                }
            }

            jdbc.update("""
                    update horses
                    set health_status = ?,
                        health_changed_by_user_id = ?,
                        health_source_type = 'MEDICAL_EXAM',
                        health_source_id = ?,
                        health_changed_at = SYSDATETIMEOFFSET(),
                        updated_at = SYSDATETIMEOFFSET(),
                        version = version + 1
                    where id = ?
                    """,
                    prevStatus, actorId, prevExamId, horseId);
        } else {
            jdbc.update("""
                    update horses
                    set health_status = 'FIT',
                        health_changed_by_user_id = ?,
                        health_source_type = null,
                        health_source_id = null,
                        health_changed_at = SYSDATETIMEOFFSET(),
                        updated_at = SYSDATETIMEOFFSET(),
                        version = version + 1
                    where id = ?
                    """,
                    actorId, horseId);
        }
    }

    private void requireInitialExamAccess(UUID horseId, UUID veterinarianId, String intakeStatus) {
        if ("PENDING_WAITING_ROOM".equals(intakeStatus) || "WAITING_FOR_VET".equals(intakeStatus)) {
            throw new BusinessException("Ngựa phải được nhân viên đưa vào phòng chờ và bác sĩ tiếp nhận trước khi ghi khám ban đầu");
        }
        if (!"IN_MEDICAL".equals(intakeStatus)) return;

        jdbc.queryForObject("select id from horses with (UPDLOCK,HOLDLOCK) where id=?", UUID.class, horseId);
        Integer assignedToThisVet = jdbc.queryForObject(
                "select count(*) from module_records where module_code='MEDICAL' "
                        + "and record_type='MEDICAL_ASSIGNMENT' and horse_id=? and user_id=? and status='ACTIVE'",
                Integer.class, horseId, veterinarianId);
        if (assignedToThisVet == null || assignedToThisVet == 0) {
            throw new BusinessException("Ca khám ban đầu đã được bác sĩ khác tiếp nhận; bạn chỉ có thể xem trạng thái");
        }
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
            throw new IllegalStateException("Dữ liệu JSON khám bệnh không hợp lệ", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Không thể serialize JSON khám bệnh", exception);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isInjuryDiagnosis(String diagnosis, String healthStatusAfter) {
        if ("INJURED".equalsIgnoreCase(healthStatusAfter)) return true;
        if (diagnosis == null) return false;
        String lower = diagnosis.toLowerCase(Locale.ROOT);
        return lower.contains("chấn thương") || lower.contains("injury") || lower.contains("gãy")
                || lower.contains("bong gân") || lower.contains("rách") || lower.contains("trật khớp");
    }

    private UUID toUuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
