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
import java.time.LocalDate;
import java.time.ZoneId;
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
     * Staff đã đưa ngựa vào phòng chờ và phí dịch vụ vẫn còn hiệu lực.
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
                where h.intake_status = 'WAITING_FOR_VET'
                  and (h.service_override=1 or (h.payment_status='PAID' and (h.paid_until is null or h.paid_until>=?)))
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
                ), LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
        );
    }

    /** Shared read-only queue state: all veterinarians can see who claimed an active case. */
    @Transactional(readOnly = true)
    public List<QueueStatusItem> getQueueOverview() {
        return jdbc.query("""
                select h.id, h.code, h.name, h.intake_status,
                       assignment.user_id as veterinarian_id, u.username as veterinarian_name
                from horses h
                outer apply (
                    select top 1 mr.user_id from module_records mr
                    where mr.horse_id=h.id and mr.module_code='MEDICAL'
                      and mr.record_type='MEDICAL_ASSIGNMENT' and mr.status='ACTIVE'
                    order by mr.created_at desc
                ) assignment
                left join users u on u.id=assignment.user_id
                where h.archived_at is null
                  and h.intake_status in ('WAITING_FOR_VET','IN_MEDICAL')
                  and (h.intake_status='IN_MEDICAL' or h.service_override=1
                       or (h.payment_status='PAID' and (h.paid_until is null or h.paid_until>=?)))
                order by h.updated_at, h.id
                """, (rs, row) -> new QueueStatusItem(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("intake_status"), rs.getObject("veterinarian_id", UUID.class),
                        rs.getString("veterinarian_name")),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    // ─── TIẾP NHẬN NGỰA ─────────────────────────────────────────

    /**
     * Bác sĩ tiếp nhận ngựa vào khám.
     * Ghi module_records, đổi intakeStatus → IN_MEDICAL; giữ nguyên paymentStatus.
     */
    @Transactional
    public AssignmentView receiveHorse(UUID horseId, UUID veterinarianId) {
        jdbc.queryForObject("select id from horses with (UPDLOCK, HOLDLOCK) where id=?", UUID.class, horseId);
        Horse horse = horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));

        // Kiểm tra trạng thái
        if (!"WAITING_FOR_VET".equals(horse.getIntakeStatus()) || !serviceEligible(horse)) {
            throw new BusinessException("Ngựa phải được nhân viên đưa vào phòng chờ và đã thanh toán hoặc được bảo lãnh");
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

        int changed = jdbc.update("update horses set intake_status='IN_MEDICAL',updated_at=SYSDATETIMEOFFSET(),version=version+1 "
                + "where id=? and intake_status='WAITING_FOR_VET'", horseId);
        if (changed != 1) throw new BusinessException("Ngựa đã được bác sĩ khác tiếp nhận");
        jdbc.update("update module_records set status='COMPLETED',updated_at=SYSDATETIMEOFFSET() "
                + "where module_code='STABLE_CARE' and record_type='WAITING_ROOM_ADMISSION' "
                + "and horse_id=? and status='ACTIVE'", horseId);

        return new AssignmentView(assignmentId, horseId, horse.getCode(), horse.getName(),
                veterinarianId, vetName, "ACTIVE", OffsetDateTime.now(), null);
    }

    // ─── HOÀN TẤT KHÁM ──────────────────────────────────────────

    /**
     * Bác sĩ hoàn tất khám → đổi assignment status → COMPLETED,
     * đổi intakeStatus → PENDING_STALL; ngựa chấn thương vẫn được phân chuồng.
     */
    @Transactional
    public AssignmentView completeMedical(UUID horseId, UUID veterinarianId) {
        jdbc.queryForObject("select id from horses with (UPDLOCK, HOLDLOCK) where id=?", UUID.class, horseId);
        Horse horse = horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));

        if (!"IN_MEDICAL".equals(horse.getIntakeStatus())) {
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

        Integer examCount = jdbc.queryForObject("select count(*) from module_records e "
                + "join module_records a on a.id=? "
                + "where e.horse_id=? and e.user_id=? and e.module_code='MEDICAL' "
                + "and e.record_type='MEDICAL_EXAMINATION' and e.created_at>=a.created_at",
                Integer.class, assignmentId, horseId, veterinarianId);
        if (examCount == null || examCount == 0) {
            throw new BusinessException("Bác sĩ cần lưu hồ sơ khám cho lần tiếp nhận này trước khi hoàn tất");
        }

        // Cập nhật assignment → COMPLETED
        jdbc.update(
                "update module_records set status='COMPLETED', data_json=JSON_MODIFY(data_json,'$.completedAt',?), "
                        + "updated_at=SYSDATETIMEOFFSET() where id=?",
                OffsetDateTime.now().toString(), assignmentId);

        int changed = jdbc.update("update horses set intake_status='PENDING_STALL',diagnosing_vet_user_id=?, "
                + "updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=? and intake_status='IN_MEDICAL'",
                veterinarianId, horseId);
        if (changed != 1) throw new BusinessException("Trạng thái tiếp nhận ngựa đã thay đổi");

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

    private boolean serviceEligible(Horse horse) {
        return Boolean.TRUE.equals(horse.getServiceOverride())
                || "PAID".equals(horse.getPaymentStatus())
                && (horse.getPaidUntil() == null || !horse.getPaidUntil().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
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

    public record QueueStatusItem(UUID horseId, String code, String name, String intakeStatus,
                                  UUID veterinarianId, String veterinarianName) {}

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
