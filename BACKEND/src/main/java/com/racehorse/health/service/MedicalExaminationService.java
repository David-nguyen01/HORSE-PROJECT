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
public class MedicalExaminationService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccessService;
    private final ObjectMapper json;

    public MedicalExaminationService(
            JdbcTemplate jdbc,
            HorseAccessService horseAccessService,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseAccessService = horseAccessService;
        this.json = json;
    }

    @Transactional
    public MedicalExaminationResponse create(
            UUID horseId,
            UUID veterinarianId,
            CreateMedicalExaminationRequest req) {
        horseAccessService.requireActive(horseId);

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
                0);
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
        horseAccessService.requireActive(horseId);

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

            return new MedicalExaminationDetailResponse(
                    id, horseId, horseCode, horseName, vetId, vetName,
                    examDate, symptoms, diagnosis, treatment, medication,
                    followUpDate, healthStatusAfter, createdAt, List.of());
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
                exam.createdAt(), addenda);
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

            return new MedicalExaminationResponse(
                    id, horseId, vetId, vetName,
                    examDate, symptoms, diagnosis, treatment, medication,
                    followUpDate, healthStatusAfter, createdAt, addendaCount);
        }, horseId);
    }

    private String normalizeHealthStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("FIT", "MONITOR", "INJURED", "QUARANTINED").contains(normalized)) {
            throw new BusinessException("Trạng thái sức khỏe chỉ nhận FIT, MONITOR, INJURED hoặc QUARANTINED");
        }
        return normalized;
    }

    @Transactional
    public void delete(UUID examId, UUID actorId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'",
                Integer.class, examId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ khám bệnh: " + examId);
        }
        // Delete addenda of this examination
        jdbc.update("delete from module_records where module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION_ADDENDUM' and JSON_VALUE(data_json, '$.examinationId')=?", examId.toString());
        // Delete examination record
        jdbc.update("delete from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'", examId);
    }

    @Transactional
    public void deleteAddendum(UUID addendumId, UUID actorId) {
        int updated = jdbc.update("delete from module_records where id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION_ADDENDUM'", addendumId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Không tìm thấy phụ lục bệnh án: " + addendumId);
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

    private UUID toUuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
