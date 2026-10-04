package com.racehorse.medical.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.racehorse.health.dto.CreateTrainingLockRequest;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.health.service.TrainingLockService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service quản lý hàng chờ tiếp nhận y tế.
 * <p>
 * Phân công bác sĩ được lưu trong module_records:
 * - module_code = 'MEDICAL'
 * - record_type = 'MEDICAL_ASSIGNMENT'
 * - horse_id = ID ngựa
 * - user_id = ID bác sĩ
 * - status: ACTIVE (đang khám), COMPLETED (đã khám xong)
 */
@Service
public class MedicalQueueService {

    private final JdbcTemplate jdbc;
    private final HorseRepository horseRepository;
    private final UserAccountRepository userRepository;
    private final NotificationService notificationService;
    private final TrainingLockService trainingLockService;
    private final TrainingLockRepository trainingLockRepository;
    private final ObjectMapper json;

    @org.springframework.beans.factory.annotation.Autowired
    public MedicalQueueService(
            JdbcTemplate jdbc,
            HorseRepository horseRepository,
            UserAccountRepository userRepository,
            NotificationService notificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TrainingLockService trainingLockService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TrainingLockRepository trainingLockRepository,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseRepository = horseRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.trainingLockService = trainingLockService;
        this.trainingLockRepository = trainingLockRepository;
        this.json = json;
    }

    public MedicalQueueService(JdbcTemplate jdbc, HorseRepository horseRepository, ObjectMapper json) {
        this(jdbc, horseRepository, null, null, null, null, json);
    }

    // ─── DANH SÁCH HÀNG CHỜ ─────────────────────────────────────

    /**
     * Lấy danh sách ngựa có paymentStatus=PAID mà chưa có MEDICAL_ASSIGNMENT active.
     * Đây là ngựa đang chờ bác sĩ tiếp nhận.
     */
    @Transactional(readOnly = true)
    public List<QueueItem> getPendingQueue() {
        return jdbc.query(
                """
                select h.id, h.code, h.name, h.breed, h.birth_date, h.gender, h.payment_status,
                       ho.owner_id, u.username as owner_name
                from horses h
                left join horse_ownerships ho on ho.horse_id = h.id
                left join users u on u.id = ho.owner_id
                where h.payment_status = 'PAID'
                  and h.archived_at is null
                  and not exists (
                      select 1 from module_records mr
                      where mr.module_code='MEDICAL' and mr.record_type='MEDICAL_ASSIGNMENT'
                        and mr.horse_id=h.id and mr.status='ACTIVE'
                  )
                order by h.updated_at asc
                """,
                (rs, n) -> new QueueItem(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("breed"),
                        rs.getObject("birth_date", java.time.LocalDate.class),
                        rs.getString("gender"),
                        rs.getString("payment_status"),
                        rs.getObject("owner_id", UUID.class),
                        rs.getString("owner_name")
                )
        );
    }

    // ─── TIẾP NHẬN NGỰA ─────────────────────────────────────────

    /**
     * Bác sĩ tiếp nhận ngựa vào khám.
     * Ghi module_records, đổi paymentStatus → IN_MEDICAL.
     */
    @Transactional
    public AssignmentView receiveHorse(UUID horseId, UUID veterinarianId) {
        Horse horse = horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));

        // Kiểm tra trạng thái
        if (!"PAID".equalsIgnoreCase(horse.getPaymentStatus())) {
            throw new BusinessException("Ngựa chưa ở trạng thái chờ tiếp nhận y tế (paymentStatus phải là PAID)");
        }

        // Kiểm tra chưa có assignment active
        Integer existing = jdbc.queryForObject(
                "select count(*) from module_records where module_code='MEDICAL' and record_type='MEDICAL_ASSIGNMENT' "
                        + "and horse_id=? and status='ACTIVE'",
                Integer.class, horseId);
        if (existing != null && existing > 0) {
            throw new BusinessException("Ngựa này đã được tiếp nhận bởi bác sĩ khác");
        }

        // Lấy tên bác sĩ
        String vetName = jdbc.queryForObject("select username from users where id=?", String.class, veterinarianId);

        // Ghi assignment
        UUID assignmentId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("veterinarianId", veterinarianId.toString());
        data.put("veterinarianName", vetName);
        data.put("receivedAt", OffsetDateTime.now().toString());

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,data_json,created_at,updated_at) "
                        + "values(?,'MEDICAL','MEDICAL_ASSIGNMENT','api',?,?,'ACTIVE',?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                assignmentId, horseId, veterinarianId, toJson(data));

        // Cập nhật paymentStatus → IN_MEDICAL
        horse.setPaymentStatus("IN_MEDICAL");
        horseRepository.save(horse);

        return new AssignmentView(assignmentId, horseId, horse.getCode(), horse.getName(),
                veterinarianId, vetName, "ACTIVE", OffsetDateTime.now(), null);
    }

    // ─── HOÀN TẤT KHÁM ──────────────────────────────────────────

    /**
     * Bác sĩ hoàn tất khám → đổi assignment status → COMPLETED,
     * đổi paymentStatus → PENDING_TRAINING.
     */
    @Transactional
    public AssignmentView completeMedical(UUID horseId, UUID veterinarianId) {
        Horse horse = horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));

        if (!"IN_MEDICAL".equalsIgnoreCase(horse.getPaymentStatus())) {
            throw new BusinessException("Ngựa không ở trạng thái đang khám y tế");
        }

        // Kiểm tra assignment
        List<UUID> assignmentIds = jdbc.query(
                "select id from module_records where module_code='MEDICAL' and record_type='MEDICAL_ASSIGNMENT' "
                        + "and horse_id=? and user_id=? and status='ACTIVE'",
                (rs, n) -> rs.getObject("id", UUID.class),
                horseId, veterinarianId);

        if (assignmentIds.isEmpty()) {
            throw new BusinessException("Bạn không phải bác sĩ đang khám ngựa này");
        }

        UUID assignmentId = assignmentIds.getFirst();

        // Cập nhật assignment → COMPLETED
        jdbc.update(
                "update module_records set status='COMPLETED', data_json=JSON_MODIFY(data_json,'$.completedAt',?), "
                        + "updated_at=SYSDATETIMEOFFSET() where id=?",
                OffsetDateTime.now().toString(), assignmentId);

        // Cập nhật paymentStatus → PENDING_TRAINING
        horse.setPaymentStatus("PENDING_TRAINING");
        horseRepository.save(horse);

        // Tự động kiểm tra và khóa huấn luyện nếu ngựa có chấn thương
        boolean isInjured = List.of("INJURED", "UNDER_TREATMENT").contains(horse.getHealthStatus());
        if (!isInjured) {
            List<Map<String, Object>> latestExams = jdbc.queryForList("""
                    select top 1 data_json from module_records
                    where horse_id=? and module_code='MEDICAL' and record_type='MEDICAL_EXAMINATION'
                    order by event_date desc, created_at desc
                    """, horseId);
            if (!latestExams.isEmpty()) {
                JsonNode n = read((String) latestExams.getFirst().get("data_json"));
                String statusAfter = n.path("healthStatusAfter").asText(null);
                String diag = n.path("diagnosis").asText("");
                if ("INJURED".equalsIgnoreCase(statusAfter) || "UNDER_TREATMENT".equalsIgnoreCase(statusAfter)
                        || diag.toLowerCase().contains("chấn thương") || diag.toLowerCase().contains("injury")) {
                    isInjured = true;
                }
            }
        }

        if (isInjured && trainingLockService != null && trainingLockRepository != null) {
            if (trainingLockRepository.findByHorseIdAndReleasedAtIsNull(horseId).isEmpty()) {
                String reason = "Khóa tự động: Ngựa chấn thương sau khi hoàn tất khám y tế ban đầu";
                trainingLockService.lock(horseId, veterinarianId, new CreateTrainingLockRequest(reason, null));
                if (userRepository != null && notificationService != null) {
                    List<UserAccount> headTrainers = userRepository.findByRoles(List.of(Role.HEAD_TRAINER));
                    for (UserAccount ht : headTrainers) {
                        notificationService.create(
                                ht,
                                "TRAINING_LOCK_AUTOMATIC",
                                NotificationPriority.HIGH,
                                "Khóa lịch tập y tế: " + horse.getCode() + " - " + horse.getName(),
                                "Ngựa " + horse.getCode() + " bị khóa lịch tập tự động do có chấn thương y tế.",
                                "HORSE",
                                horseId);
                    }
                }
            }
        }

        if (userRepository != null && notificationService != null) {
            List<UserAccount> staffMembers = userRepository.findByRoles(List.of(Role.STABLE_STAFF));
            for (UserAccount staff : staffMembers) {
                notificationService.create(
                        staff,
                        "STABLE_ASSIGNMENT_REQUEST",
                        NotificationPriority.HIGH,
                        "Hoàn tất khám y tế: " + horse.getName() + " (" + horse.getCode() + ")",
                        "Ngựa " + horse.getCode() + " đã hoàn thành khám y tế ban đầu. Vui lòng kiểm tra và sắp xếp ô chuồng.",
                        "HORSE",
                        horse.getId());
            }
        }

        String vetName = jdbc.queryForObject("select username from users where id=?", String.class, veterinarianId);

        return new AssignmentView(assignmentId, horseId, horse.getCode(), horse.getName(),
                veterinarianId, vetName, "COMPLETED", null, OffsetDateTime.now());
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    // ─── DANH SÁCH NGỰA ĐANG KHÁM CỦA BÁC SĨ ───────────────────

    @Transactional(readOnly = true)
    public List<AssignmentView> getMyAssignments(UUID veterinarianId) {
        return jdbc.query(
                "select mr.id, mr.horse_id, h.code, h.name, mr.user_id, u.username, mr.status, mr.created_at "
                        + "from module_records mr "
                        + "join horses h on h.id = mr.horse_id "
                        + "join users u on u.id = mr.user_id "
                        + "where mr.module_code='MEDICAL' and mr.record_type='MEDICAL_ASSIGNMENT' "
                        + "and mr.user_id=? and mr.status='ACTIVE' "
                        + "order by mr.created_at asc",
                (rs, n) -> new AssignmentView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("horse_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("username"),
                        rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        null
                ),
                veterinarianId
        );
    }

    // ─── VIEW DTO ────────────────────────────────────────────────

    public record QueueItem(
            UUID horseId, String code, String name, String breed,
            java.time.LocalDate birthDate, String gender, String paymentStatus,
            UUID ownerId, String ownerName
    ) {
    }

    public record AssignmentView(
            UUID assignmentId, UUID horseId, String horseCode, String horseName,
            UUID veterinarianId, String veterinarianName, String status,
            OffsetDateTime receivedAt, OffsetDateTime completedAt
    ) {
    }

    // ─── JSON HELPER ─────────────────────────────────────────────

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Không thể tạo dữ liệu JSON", e);
        }
    }
}
