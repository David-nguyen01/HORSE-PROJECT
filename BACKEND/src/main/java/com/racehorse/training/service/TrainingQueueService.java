package com.racehorse.training.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.repository.HorseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service quản lý hàng chờ tiếp nhận huấn luyện.
 * <p>
 * Phân công trainer được lưu trong module_records:
 * - module_code = 'TRAINING'
 * - record_type = 'TRAINER_ASSIGNMENT'
 * - horse_id = ID ngựa
 * - user_id = ID trainer
 * - status: ACTIVE (đang huấn luyện), COMPLETED (hoàn tất onboarding)
 */
@Service
public class TrainingQueueService {

    private final JdbcTemplate jdbc;
    private final HorseRepository horseRepository;
    private final UserAccountRepository userRepository;
    private final ObjectMapper json;

    public TrainingQueueService(JdbcTemplate jdbc, HorseRepository horseRepository,
                                UserAccountRepository userRepository, ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseRepository = horseRepository;
        this.userRepository = userRepository;
        this.json = json;
    }

    // ─── DANH SÁCH HÀNG CHỜ ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QueueItem> getPendingQueue() {
        return jdbc.query(
                """
                select h.id, h.code, h.name, h.breed, h.birth_date, h.gender, h.payment_status,
                       ho.owner_id, u.username as owner_name
                from horses h
                left join horse_ownerships ho on ho.horse_id = h.id
                left join users u on u.id = ho.owner_id
                where h.payment_status = 'PENDING_TRAINING'
                  and h.archived_at is null
                  and h.current_trainer_id is null
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

    @Transactional
    public AssignmentView receiveHorse(UUID horseId, UUID trainerId) {
        Horse horse = horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));

        if (!"PENDING_TRAINING".equalsIgnoreCase(horse.getPaymentStatus())) {
            throw new BusinessException("Ngựa chưa sẵn sàng huấn luyện (cần hoàn tất khám y tế hoặc đang chờ huấn luyện)");
        }

        if (horse.getCurrentTrainer() != null) {
            throw new BusinessException("Ngựa này đã được tiếp nhận bởi huấn luyện viên khác");
        }

        String trainerName = jdbc.queryForObject("select username from users where id=?", String.class, trainerId);

        // Ghi assignment
        UUID assignmentId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trainerId", trainerId.toString());
        data.put("trainerName", trainerName);
        data.put("receivedAt", OffsetDateTime.now().toString());

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,data_json,created_at,updated_at) "
                        + "values(?,'TRAINING','TRAINER_ASSIGNMENT','api',?,?,'ACTIVE',?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                assignmentId, horseId, trainerId, toJson(data));

        // Cập nhật paymentStatus → IN_TRAINING
        horse.setPaymentStatus("IN_TRAINING");
        // Gán trainer chính cho ngựa (tái sử dụng trường currentTrainer)
        UserAccount trainer = userRepository.findById(trainerId).orElse(null);
        horse.assignCurrentTrainer(trainer);
        try {
            horseRepository.saveAndFlush(horse);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Tranh chấp dữ liệu: Ngựa này vừa được một huấn luyện viên khác tiếp nhận. Vui lòng làm mới trang.");
        }

        return new AssignmentView(assignmentId, horseId, horse.getCode(), horse.getName(),
                trainerId, trainerName, "ACTIVE", OffsetDateTime.now(), null);
    }

    // ─── HOÀN TẤT ONBOARDING ────────────────────────────────────

    @Transactional
    public AssignmentView completeOnboarding(UUID horseId, UUID trainerId) {
        Horse horse = horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));

        if (!"IN_TRAINING".equalsIgnoreCase(horse.getPaymentStatus())) {
            throw new BusinessException("Ngựa không ở trạng thái đang huấn luyện");
        }

        List<UUID> assignmentIds = jdbc.query(
                "select id from module_records where module_code='TRAINING' and record_type='TRAINER_ASSIGNMENT' "
                        + "and horse_id=? and user_id=? and status='ACTIVE'",
                (rs, n) -> rs.getObject("id", UUID.class),
                horseId, trainerId);

        if (assignmentIds.isEmpty()) {
            throw new BusinessException("Bạn không phải huấn luyện viên đang phụ trách ngựa này");
        }

        UUID assignmentId = assignmentIds.getFirst();

        // Cập nhật assignment → COMPLETED
        jdbc.update(
                "update module_records set status='COMPLETED', data_json=JSON_MODIFY(data_json,'$.completedAt',?), "
                        + "updated_at=SYSDATETIMEOFFSET() where id=?",
                OffsetDateTime.now().toString(), assignmentId);

        // Cập nhật paymentStatus → COMPLETED_ONBOARDING (ngựa chính thức hoạt động)
        horse.setPaymentStatus("COMPLETED_ONBOARDING");
        horseRepository.save(horse);

        String trainerName = jdbc.queryForObject("select username from users where id=?", String.class, trainerId);

        return new AssignmentView(assignmentId, horseId, horse.getCode(), horse.getName(),
                trainerId, trainerName, "COMPLETED", null, OffsetDateTime.now());
    }

    // ─── DANH SÁCH NGỰA ĐANG HUẤN LUYỆN CỦA TRAINER ─────────────

    @Transactional(readOnly = true)
    public List<AssignmentView> getMyAssignments(UUID trainerId) {
        return jdbc.query(
                "select mr.id, mr.horse_id, h.code, h.name, mr.user_id, u.username, mr.status, mr.created_at "
                        + "from module_records mr "
                        + "join horses h on h.id = mr.horse_id "
                        + "join users u on u.id = mr.user_id "
                        + "where mr.module_code='TRAINING' and mr.record_type='TRAINER_ASSIGNMENT' "
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
                trainerId
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
            UUID trainerId, String trainerName, String status,
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
