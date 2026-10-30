package com.racehorse.medical.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FollowUpService {
    public static final String COVER_VISIT = "COVER_VISIT";
    public static final String TRANSFER_CARE = "TRANSFER_CARE";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserAccountRepository users;
    private final NotificationService notifications;

    public FollowUpService(JdbcTemplate jdbc, ObjectMapper json, UserAccountRepository users,
                           NotificationService notifications) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<FollowUpView> listOpen() {
        return jdbc.query("""
                select f.id, f.horse_id, h.code, h.name, f.event_date, f.user_id,
                       u.username as veterinarian_name, f.status,
                       JSON_VALUE(f.data_json,'$.sourceExaminationId') as source_examination_id,
                       JSON_VALUE(f.data_json,'$.handoverReason') as handover_reason,
                       JSON_VALUE(f.data_json,'$.handoverMode') as handover_mode,
                       JSON_VALUE(f.data_json,'$.visitType') as visit_type
                from module_records f
                join horses h on h.id=f.horse_id
                join users u on u.id=f.user_id
                where f.module_code='MEDICAL' and f.record_type='MEDICAL_FOLLOW_UP'
                  and f.status in ('ASSIGNED','HANDOVER_REQUESTED') and h.archived_at is null
                order by f.event_date, f.created_at
                """, (rs, row) -> new FollowUpView(
                rs.getObject("id", UUID.class), rs.getObject("horse_id", UUID.class),
                rs.getString("code"), rs.getString("name"),
                rs.getObject("event_date", LocalDate.class), rs.getObject("user_id", UUID.class),
                rs.getString("veterinarian_name"), rs.getString("status"),
                rs.getString("source_examination_id"), rs.getString("handover_reason"),
                rs.getString("handover_mode"), rs.getString("visit_type")));
    }

    @Transactional
    public FollowUpView requestHandover(UUID followUpId, UUID veterinarianId, String reason) {
        return requestHandover(followUpId, veterinarianId, reason, COVER_VISIT);
    }

    @Transactional
    public FollowUpView requestHandover(UUID followUpId, UUID veterinarianId, String reason, String mode) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
            throw new BusinessException("Cần ghi lý do bàn giao (tối đa 1000 ký tự)");
        }
        String normalizedMode = mode == null ? COVER_VISIT : mode.trim().toUpperCase();
        if (!List.of(COVER_VISIT, TRANSFER_CARE).contains(normalizedMode)) {
            throw new BusinessException("mode chỉ nhận COVER_VISIT hoặc TRANSFER_CARE");
        }
        UUID horseId = lockHorseForFollowUp(followUpId);
        if (TRANSFER_CARE.equals(normalizedMode)) {
            Integer isPrimaryVet = jdbc.queryForObject(
                    "select count(*) from horses where id=? and diagnosing_vet_user_id=? and archived_at is null",
                    Integer.class, horseId, veterinarianId);
            if (isPrimaryVet == null || isPrimaryVet != 1) {
                throw new BusinessException("Chỉ bác sĩ phụ trách ngựa mới được chuyển quyền phụ trách lâu dài");
            }
        }
        int changed = jdbc.update("""
                update module_records set status='HANDOVER_REQUESTED',
                    data_json=JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(data_json,'$.handoverReason',?),
                                          '$.handoverMode',?),
                                          '$.handoverRequestedAt',CONVERT(varchar(33),SYSDATETIMEOFFSET(),127)),
                    updated_at=SYSDATETIMEOFFSET()
                where id=? and horse_id=? and module_code='MEDICAL' and record_type='MEDICAL_FOLLOW_UP'
                  and status='ASSIGNED' and user_id=?
                """, reason.trim(), normalizedMode, followUpId, horseId, veterinarianId);
        if (changed != 1) throw new BusinessException("Chỉ bác sĩ đang phụ trách mới được gửi yêu cầu bàn giao ca chưa bàn giao");
        FollowUpView view = get(followUpId);
        for (UserAccount vet : users.findByRoles(List.of(Role.VETERINARIAN))) {
            if (vet.getId().equals(veterinarianId)) continue;
            notifications.create(vet, "MEDICAL_FOLLOW_UP_HANDOVER", NotificationPriority.HIGH,
                    (TRANSFER_CARE.equals(normalizedMode) ? "Bàn giao ngựa: " : "Cần khám thay một lần: ") + view.horseName(),
                    "Ngựa " + view.horseCode() + " hẹn tái khám ngày " + view.dueDate() + ". Lý do bàn giao: " + reason.trim(),
                    "HORSE", horseId);
        }
        return view;
    }

    @Transactional
    public FollowUpView acceptHandover(UUID followUpId, UUID veterinarianId) {
        UUID horseId = lockHorseForFollowUp(followUpId);
        List<UUID> previousVets = jdbc.query("""
                select user_id from module_records where id=? and horse_id=? and module_code='MEDICAL'
                  and record_type='MEDICAL_FOLLOW_UP' and status='HANDOVER_REQUESTED'
                """, (rs, row) -> rs.getObject(1, UUID.class), followUpId, horseId);
        if (previousVets.isEmpty() || previousVets.getFirst().equals(veterinarianId)) {
            throw new BusinessException("Ca chưa được bác sĩ khác yêu cầu bàn giao");
        }
        int changed = jdbc.update("""
                update module_records set user_id=?, status='ASSIGNED',
                    data_json=JSON_MODIFY(JSON_MODIFY(data_json,'$.acceptedBy',CONVERT(varchar(36),?)),
                                          '$.acceptedAt',CONVERT(varchar(33),SYSDATETIMEOFFSET(),127)),
                    updated_at=SYSDATETIMEOFFSET()
                where id=? and horse_id=? and module_code='MEDICAL' and record_type='MEDICAL_FOLLOW_UP'
                  and status='HANDOVER_REQUESTED' and user_id<>?
                """, veterinarianId, veterinarianId, followUpId, horseId, veterinarianId);
        if (changed != 1) throw new BusinessException("Ca đã được bác sĩ khác tiếp nhận hoặc chưa có yêu cầu bàn giao");
        FollowUpView view = get(followUpId);
        if (TRANSFER_CARE.equals(view.handoverMode())) {
            int transferred = jdbc.update("update horses set diagnosing_vet_user_id=?, "
                    + "updated_at=SYSDATETIMEOFFSET(),version=version+1 "
                    + "where id=? and diagnosing_vet_user_id=? and archived_at is null",
                    veterinarianId, horseId, previousVets.getFirst());
            if (transferred != 1) throw new BusinessException("Bác sĩ phụ trách ngựa đã thay đổi; vui lòng tải lại");
        }
        users.findById(previousVets.getFirst()).ifPresent(previousVet -> notifications.create(previousVet,
                "MEDICAL_FOLLOW_UP_ACCEPTED", NotificationPriority.NORMAL,
                "Đã có bác sĩ nhận ca tái khám: " + view.horseName(),
                view.veterinarianName() + " đã nhận ca ngày " + view.dueDate() + ".",
                "HORSE", horseId));
        return view;
    }

    @Transactional
    public FollowUpView cancelHandover(UUID followUpId, UUID veterinarianId) {
        UUID horseId = lockHorseForFollowUp(followUpId);
        int changed = jdbc.update("""
                update module_records set status='ASSIGNED',
                    data_json=JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(data_json,'$.handoverReason',NULL),
                                           '$.handoverMode',NULL),'$.handoverRequestedAt',NULL),
                    updated_at=SYSDATETIMEOFFSET()
                where id=? and horse_id=? and module_code='MEDICAL' and record_type='MEDICAL_FOLLOW_UP'
                  and status='HANDOVER_REQUESTED' and user_id=?
                """, followUpId, horseId, veterinarianId);
        if (changed != 1) throw new BusinessException("Chỉ bác sĩ đã gửi yêu cầu và chưa có người nhận mới được rút yêu cầu");
        return get(followUpId);
    }

    /** Called inside the examination transaction after the horse row is locked. */
    public UUID requireAssignedForExamination(UUID horseId, UUID veterinarianId) {
        List<Map<String, Object>> open = jdbc.queryForList("""
                select id, user_id, status from module_records
                where horse_id=? and module_code='MEDICAL' and record_type='MEDICAL_FOLLOW_UP'
                  and status in ('ASSIGNED','HANDOVER_REQUESTED')
                """, horseId);
        if (open.isEmpty()) return null;
        if (open.size() > 1) throw new BusinessException("Có nhiều ca tái khám đang mở cho cùng một ngựa");
        Map<String, Object> caseRow = open.getFirst();
        if (!"ASSIGNED".equals(caseRow.get("status")) || !veterinarianId.equals(toUuid(caseRow.get("user_id")))) {
            throw new BusinessException("Ca tái khám đang chờ bàn giao hoặc đã được bác sĩ khác tiếp nhận; bạn chỉ có thể xem");
        }
        return toUuid(caseRow.get("id"));
    }

    /** Completes this visit and schedules the next required check no later than one calendar month. */
    public void recordExamination(UUID horseId, UUID veterinarianId, UUID examinationId,
                                  UUID currentFollowUpId, LocalDate examinationDate, LocalDate requestedFollowUpDate) {
        if (currentFollowUpId != null) {
            int changed = jdbc.update("""
                    update module_records set status='COMPLETED',
                        data_json=JSON_MODIFY(data_json,'$.completedExaminationId',CONVERT(varchar(36),?)),
                        updated_at=SYSDATETIMEOFFSET()
                    where id=? and horse_id=? and user_id=? and status='ASSIGNED'
                    """, examinationId, currentFollowUpId, horseId, veterinarianId);
            if (changed != 1) throw new BusinessException("Ca tái khám đã thay đổi, vui lòng tải lại");
        }
        LocalDate monthlyDue = examinationDate.plusMonths(1);
        LocalDate nextDate = requestedFollowUpDate != null && requestedFollowUpDate.isBefore(monthlyDue)
                ? requestedFollowUpDate : monthlyDue;
        List<UUID> primaryDoctors = jdbc.query("select diagnosing_vet_user_id from horses where id=?",
                (rs, row) -> rs.getObject(1, UUID.class), horseId);
        UUID nextVeterinarianId = primaryDoctors.isEmpty() || primaryDoctors.getFirst() == null
                ? veterinarianId : primaryDoctors.getFirst();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceExaminationId", examinationId.toString());
        data.put("assignedVeterinarianId", nextVeterinarianId.toString());
        data.put("visitType", requestedFollowUpDate != null && requestedFollowUpDate.isBefore(monthlyDue)
                ? "CLINICAL" : "MONTHLY");
        try {
            jdbc.update("""
                    insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,
                                               status,event_date,data_json,created_at,updated_at)
                    values(?,'MEDICAL','MEDICAL_FOLLOW_UP','medical_examinations',?,?,'ASSIGNED',?,?,
                           SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())
                    """, UUID.randomUUID(), horseId, nextVeterinarianId, nextDate, json.writeValueAsString(data));
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Không thể lưu lịch tái khám", exception);
        }
    }

    /** Emergency treatment does not transfer the horse or consume another doctor's assigned visit. */
    public void recordEmergencyExamination(UUID horseId, UUID examinationId,
                                           LocalDate examinationDate, LocalDate requestedFollowUpDate) {
        List<UUID> primaryDoctors = jdbc.query("select diagnosing_vet_user_id from horses where id=?",
                (rs, row) -> rs.getObject(1, UUID.class), horseId);
        if (primaryDoctors.isEmpty() || primaryDoctors.getFirst() == null) return;
        UUID primaryDoctor = primaryDoctors.getFirst();
        LocalDate monthlyDue = examinationDate.plusMonths(1);
        LocalDate due = requestedFollowUpDate != null && requestedFollowUpDate.isBefore(monthlyDue)
                ? requestedFollowUpDate : monthlyDue;
        int updated = jdbc.update("""
                update module_records set event_date=case when event_date>? then ? else event_date end,
                    updated_at=SYSDATETIMEOFFSET()
                where horse_id=? and module_code='MEDICAL' and record_type='MEDICAL_FOLLOW_UP'
                  and status in ('ASSIGNED','HANDOVER_REQUESTED')
                """, due, due, horseId);
        if (updated > 0) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceExaminationId", examinationId.toString());
        data.put("assignedVeterinarianId", primaryDoctor.toString());
        data.put("visitType", requestedFollowUpDate != null && requestedFollowUpDate.isBefore(monthlyDue)
                ? "CLINICAL" : "MONTHLY");
        try {
            jdbc.update("""
                    insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,
                                               status,event_date,data_json,created_at,updated_at)
                    values(?,'MEDICAL','MEDICAL_FOLLOW_UP','medical_examinations',?,?,'ASSIGNED',?,?,
                           SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())
                    """, UUID.randomUUID(), horseId, primaryDoctor, due, json.writeValueAsString(data));
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Không thể lưu lịch tái khám sau cấp cứu", exception);
        }
    }

    private UUID lockHorseForFollowUp(UUID followUpId) {
        List<UUID> horses = jdbc.query("""
                select horse_id from module_records
                where id=? and module_code='MEDICAL' and record_type='MEDICAL_FOLLOW_UP'
                """, (rs, row) -> rs.getObject(1, UUID.class), followUpId);
        if (horses.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy ca tái khám: " + followUpId);
        UUID horseId = horses.getFirst();
        jdbc.queryForObject("select id from horses with (UPDLOCK,HOLDLOCK) where id=?", UUID.class, horseId);
        return horseId;
    }

    private FollowUpView get(UUID followUpId) {
        return listOpen().stream().filter(item -> item.id().equals(followUpId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca tái khám đang mở: " + followUpId));
    }

    private UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    public record FollowUpView(UUID id, UUID horseId, String horseCode, String horseName,
                               LocalDate dueDate, UUID veterinarianId, String veterinarianName,
                               String status, String sourceExaminationId, String handoverReason,
                               String handoverMode, String visitType) {}
}
