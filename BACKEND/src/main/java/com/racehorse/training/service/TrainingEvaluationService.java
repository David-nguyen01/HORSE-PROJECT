package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.training.dto.CreateTrainingEvaluationRequest;
import com.racehorse.training.dto.CreateVetAssessmentRequest;
import com.racehorse.training.dto.TrainingEvaluationResponse;
import com.racehorse.training.dto.VetAssessmentResponse;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class TrainingEvaluationService {

    private final TrainingEvaluationRepository repository;
    private final UserAccountRepository userRepository;
    private final HorseAccessService horseAccessService;
    private final TrainingScheduleRepository scheduleRepository;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TrainingEvaluationService(
            TrainingEvaluationRepository repository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            TrainingScheduleRepository scheduleRepository,
            NotificationService notificationService,
            JdbcTemplate jdbc,
            ObjectMapper json,
            Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.horseAccessService = horseAccessService;
        this.scheduleRepository = scheduleRepository;
        this.notificationService = notificationService;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public TrainingEvaluationService(
            TrainingEvaluationRepository repository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            TrainingScheduleRepository scheduleRepository,
            Clock clock) {
        this(repository, userRepository, horseAccessService, scheduleRepository, null, null, null, clock);
    }

    public TrainingEvaluationService(
            TrainingEvaluationRepository repository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            Clock clock) {
        this(repository, userRepository, horseAccessService, null, null, null, null, clock);
    }

    @Transactional
    public TrainingEvaluationResponse create(UUID actorId, CreateTrainingEvaluationRequest request) {
        Horse horse = horseAccessService.requireActive(request.horseId());
        UserAccount actor = userRepository.findById(actorId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người ghi nhận: " + actorId));
        TrainingSchedule schedule = null;
        if (request.trainingScheduleId() != null) {
            if (scheduleRepository == null) {
                throw new IllegalStateException("scheduleRepository is not configured");
            }
            schedule = scheduleRepository.findDetailById(request.trainingScheduleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + request.trainingScheduleId()));
            if (!schedule.getHorse().getId().equals(horse.getId())) {
                throw new BusinessException("Lịch huấn luyện không thuộc về ngựa được đánh giá");
            }
            if (schedule.getCancelledAt() != null) {
                throw new BusinessException("Không thể ghi nhận đánh giá cho lịch huấn luyện đã bị hủy");
            }
            if (repository.existsByTrainingScheduleId(schedule.getId())) {
                throw new BusinessException("Lịch huấn luyện này đã có đánh giá");
            }
            schedule.complete(clock.instant());
        }
        String videoUrl = normalizeAndValidateVideoUrl(request.videoUrl());
        TrainingEvaluation evaluation = new TrainingEvaluation(
                horse,
                schedule,
                actor,
                request.sessionDate(),
                request.durationMinutes(),
                request.distanceMeters(),
                request.averageSpeedKmh(),
                request.trainingLoad(),
                request.heartRateRecoveryBpm(),
                request.perceivedExertion(),
                request.fitnessStatus(),
                request.trainerNotes().trim(),
                videoUrl,
                clock.instant());
        return toResponse(repository.save(evaluation));
    }

    @Transactional(readOnly = true)
    public TrainingEvaluationResponse getById(UUID evaluationId, UUID actorId, boolean privileged) {
        TrainingEvaluation evaluation = findDetail(evaluationId);
        horseAccessService.requireReadable(evaluation.getHorse().getId(), actorId, privileged);
        return toResponse(evaluation);
    }

    @Transactional(readOnly = true)
    public Page<TrainingEvaluationResponse> listForHorse(
            UUID horseId,
            UUID actorId,
            boolean privileged,
            Pageable pageable) {
        horseAccessService.requireReadable(horseId, actorId, privileged);
        return repository.findByHorseIdOrderBySessionDateDescCompletedAtDesc(horseId, pageable)
                .map(this::toResponse);
    }

    private TrainingEvaluation findDetail(UUID id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá buổi tập: " + id));
    }

    private String normalizeAndValidateVideoUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Liên kết video phải là URL HTTP hoặc HTTPS hợp lệ");
        }
        return normalized;
    }

    TrainingEvaluationResponse toResponse(TrainingEvaluation evaluation) {
        return new TrainingEvaluationResponse(
                evaluation.getId(),
                new TrainingEvaluationResponse.HorseSummary(
                        evaluation.getHorse().getId(),
                        evaluation.getHorse().getCode(),
                        evaluation.getHorse().getName()),
                evaluation.getTrainingSchedule() != null ? evaluation.getTrainingSchedule().getId() : null,
                new TrainingEvaluationResponse.UserSummary(
                        evaluation.getRecordedBy().getId(),
                        evaluation.getRecordedBy().getUsername()),
                evaluation.getSessionDate(),
                evaluation.getDurationMinutes(),
                evaluation.getDistanceMeters(),
                evaluation.getAverageSpeedKmh(),
                evaluation.getTrainingLoad(),
                evaluation.getHeartRateRecoveryBpm(),
                evaluation.getPerceivedExertion(),
                evaluation.getFitnessStatus(),
                evaluation.getTrainerNotes(),
                evaluation.getVideoUrl(),
                evaluation.getCompletedAt());
    }

    @Transactional
    public VetAssessmentResponse saveVetAssessment(UUID scheduleId, UUID vetId, CreateVetAssessmentRequest request) {
        if (scheduleRepository == null || jdbc == null) {
            throw new IllegalStateException("Dependencies not configured");
        }
        TrainingSchedule schedule = scheduleRepository.findDetailById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + scheduleId));
        UserAccount vet = userRepository.findById(vetId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bác sĩ thú y: " + vetId));

        UUID assessmentId = UUID.randomUUID();
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scheduleId", scheduleId.toString());
        data.put("overallCondition", request.overallCondition().trim());
        data.put("heartRateRecoveryBpm", request.heartRateRecoveryBpm());
        data.put("respiratoryStatus", request.respiratoryStatus().trim());
        data.put("gaitStatus", request.gaitStatus().trim());
        data.put("muscleCondition", request.muscleCondition().trim());
        data.put("clinicalConclusion", request.clinicalConclusion().trim());
        data.put("recommendation", request.recommendation() != null ? request.recommendation().trim() : null);
        data.put("requiresMedicalIntervention", Boolean.TRUE.equals(request.requiresMedicalIntervention()));
        data.put("veterinarianId", vetId.toString());
        data.put("veterinarianName", vet.getUsername());
        data.put("createdAt", now.toString());

        jdbc.update("""
                insert into module_records(
                    id, module_code, record_type, source_table,
                    horse_id, user_id, status, event_date, data_json,
                    created_at, updated_at
                ) values (?, 'TRAINING', 'VET_TRAINING_ASSESSMENT', 'training_schedules',
                          ?, ?, 'COMPLETED', ?, ?,
                          SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())
                """,
                assessmentId, schedule.getHorse().getId(), vetId, today, toJson(data));

        if (notificationService != null) {
            List<UserAccount> headTrainers = userRepository.findByRoles(List.of(Role.HEAD_TRAINER));
            for (UserAccount ht : headTrainers) {
                notificationService.create(
                        ht,
                        "VET_ASSESSMENT_COMPLETED",
                        NotificationPriority.NORMAL,
                        "Đánh giá lâm sàng: " + schedule.getHorse().getCode() + " - " + schedule.getHorse().getName(),
                        "Bác sĩ " + vet.getUsername() + " đã gửi đánh giá lâm sàng buổi tập: " + request.clinicalConclusion().trim(),
                        "TRAINING_SCHEDULE",
                        scheduleId);
            }
        }

        return new VetAssessmentResponse(
                assessmentId,
                scheduleId,
                schedule.getHorse().getId(),
                schedule.getHorse().getCode(),
                schedule.getHorse().getName(),
                vetId,
                vet.getUsername(),
                request.overallCondition().trim(),
                request.heartRateRecoveryBpm(),
                request.respiratoryStatus().trim(),
                request.gaitStatus().trim(),
                request.muscleCondition().trim(),
                request.clinicalConclusion().trim(),
                request.recommendation() != null ? request.recommendation().trim() : null,
                Boolean.TRUE.equals(request.requiresMedicalIntervention()),
                now);
    }

    @Transactional(readOnly = true)
    public VetAssessmentResponse getVetAssessment(UUID scheduleId) {
        if (jdbc == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select top 1 m.id, m.horse_id, h.code as horse_code, h.name as horse_name,
                       m.user_id, u.username as vet_name, m.data_json, m.created_at
                from module_records m
                join horses h on h.id = m.horse_id
                join users u on u.id = m.user_id
                where m.module_code = 'TRAINING' and m.record_type = 'VET_TRAINING_ASSESSMENT'
                  and JSON_VALUE(m.data_json, '$.scheduleId') = ?
                order by m.created_at desc
                """, scheduleId.toString());
        if (rows.isEmpty()) return null;
        return mapVetAssessmentRow(rows.getFirst());
    }

    @Transactional(readOnly = true)
    public List<VetAssessmentResponse> getVetAssessmentsByDate(LocalDate date) {
        if (jdbc == null) return List.of();
        return jdbc.query("""
                select m.id, m.horse_id, h.code as horse_code, h.name as horse_name,
                       m.user_id, u.username as vet_name, m.data_json, m.created_at
                from module_records m
                join horses h on h.id = m.horse_id
                join users u on u.id = m.user_id
                where m.module_code = 'TRAINING' and m.record_type = 'VET_TRAINING_ASSESSMENT'
                  and m.event_date = ?
                order by m.created_at desc
                """, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getObject("id"));
            map.put("horse_id", rs.getObject("horse_id"));
            map.put("horse_code", rs.getString("horse_code"));
            map.put("horse_name", rs.getString("horse_name"));
            map.put("user_id", rs.getObject("user_id"));
            map.put("vet_name", rs.getString("vet_name"));
            map.put("data_json", rs.getString("data_json"));
            map.put("created_at", rs.getObject("created_at"));
            return mapVetAssessmentRow(map);
        }, date);
    }

    private String toJson(Object value) {
        if (json == null) return "{}";
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi tạo JSON: " + e.getMessage(), e);
        }
    }

    private VetAssessmentResponse mapVetAssessmentRow(Map<String, Object> row) {
        UUID id = (UUID) row.get("id");
        UUID horseId = (UUID) row.get("horse_id");
        String horseCode = (String) row.get("horse_code");
        String horseName = (String) row.get("horse_name");
        UUID vetId = (UUID) row.get("user_id");
        String vetName = (String) row.get("vet_name");
        Object createdAtObj = row.get("created_at");
        OffsetDateTime createdAt = createdAtObj instanceof OffsetDateTime odt
                ? odt : (createdAtObj instanceof java.sql.Timestamp ts ? ts.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());

        String jsonStr = (String) row.get("data_json");
        JsonNode node;
        try {
            node = json != null ? json.readTree(jsonStr) : new ObjectMapper().readTree(jsonStr);
        } catch (Exception e) {
            node = null;
        }

        UUID schedId = node != null && !node.path("scheduleId").isNull() && !node.path("scheduleId").asText().isBlank()
                ? UUID.fromString(node.path("scheduleId").asText()) : null;
        String overallCondition = node != null ? node.path("overallCondition").asText("GOOD") : "GOOD";
        int heartRate = node != null ? node.path("heartRateRecoveryBpm").asInt(60) : 60;
        String resp = node != null ? node.path("respiratoryStatus").asText("NORMAL") : "NORMAL";
        String gait = node != null ? node.path("gaitStatus").asText("NORMAL") : "NORMAL";
        String muscle = node != null ? node.path("muscleCondition").asText("GOOD") : "GOOD";
        String conclusion = node != null ? node.path("clinicalConclusion").asText("") : "";
        String rec = node != null && !node.path("recommendation").isNull() ? node.path("recommendation").asText(null) : null;
        boolean reqMed = node != null && node.path("requiresMedicalIntervention").asBoolean(false);

        return new VetAssessmentResponse(
                id, schedId, horseId, horseCode, horseName, vetId, vetName,
                overallCondition, heartRate, resp, gait, muscle, conclusion, rec, reqMed, createdAt);
    }
}
