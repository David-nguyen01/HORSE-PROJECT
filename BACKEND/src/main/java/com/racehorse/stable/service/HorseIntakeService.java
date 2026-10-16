package com.racehorse.stable.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class HorseIntakeService {
    private final JdbcTemplate jdbc;
    private final HorseAccessService access;
    private final UserAccountRepository users;
    private final NotificationService notifications;

    @org.springframework.beans.factory.annotation.Autowired
    public HorseIntakeService(JdbcTemplate jdbc, HorseAccessService access,
            UserAccountRepository users, NotificationService notifications) {
        this.jdbc = jdbc;
        this.access = access;
        this.users = users;
        this.notifications = notifications;
    }

    public HorseIntakeService(JdbcTemplate jdbc, HorseAccessService access) {
        this(jdbc, access, null, null);
    }

    @Transactional(readOnly = true)
    public List<IntakeHorse> pendingWaitingRoom() {
        return list("PENDING_WAITING_ROOM");
    }

    @Transactional(readOnly = true)
    public List<IntakeHorse> pendingStall() {
        return list("PENDING_STALL");
    }

    private List<IntakeHorse> list(String status) {
        return jdbc.query("""
                select h.id, h.code, h.name, h.payment_status, h.intake_status,
                       h.diagnosing_vet_user_id, u.username as diagnosing_vet_name,
                       h.health_status
                from horses h
                left join users u on u.id = h.diagnosing_vet_user_id
                where h.archived_at is null and h.intake_status=?
                  and (h.service_override=1 or (h.payment_status='PAID' and (h.paid_until is null or h.paid_until>=?)))
                order by h.updated_at, h.id
                """,
                (rs, row) -> new IntakeHorse(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("payment_status"),
                        rs.getString("intake_status"),
                        rs.getObject("diagnosing_vet_user_id", UUID.class),
                        rs.getString("diagnosing_vet_name"),
                        rs.getString("health_status")),
                status, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Transactional
    public IntakeHorse admitToWaitingRoom(UUID horseId, UUID staffId) {
        jdbc.queryForObject("select id from horses with (UPDLOCK, HOLDLOCK) where id=?", UUID.class, horseId);
        Horse horse = access.requireServiceEligible(horseId);
        if (!"PENDING_WAITING_ROOM".equals(horse.getIntakeStatus())) {
            throw new BusinessException("Ngựa không ở bước chờ nhân viên đưa vào phòng chờ khám");
        }
        int changed = jdbc.update("update horses set intake_status='WAITING_FOR_VET', "
                        + "updated_at=SYSDATETIMEOFFSET(),version=version+1 "
                        + "where id=? and intake_status='PENDING_WAITING_ROOM'", horseId);
        if (changed != 1) throw new BusinessException("Ngựa đã được tiếp nhận bởi nhân viên khác");
        jdbc.update("insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,data_json) "
                        + "values(?,'STABLE_CARE','WAITING_ROOM_ADMISSION','api',?,?,'ACTIVE','{}')",
                UUID.randomUUID(), horseId, staffId);
        if (users != null && notifications != null) {
            for (UserAccount veterinarian : users.findByRoles(List.of(Role.VETERINARIAN))) {
                notifications.create(veterinarian, "HORSE_WAITING_FOR_VET", NotificationPriority.HIGH,
                        "Ngựa chờ khám: " + horse.getCode(),
                        "Ngựa " + horse.getName() + " đã vào phòng chờ. Bác sĩ đầu tiên tiếp nhận sẽ phụ trách ca khám ban đầu.",
                        "HORSE", horseId);
            }
        }
        return new IntakeHorse(horseId, horse.getCode(), horse.getName(), horse.getPaymentStatus(), "WAITING_FOR_VET");
    }
    public record IntakeHorse(
            UUID horseId,
            String code,
            String name,
            String paymentStatus,
            String intakeStatus,
            UUID diagnosingVetId,
            String diagnosingVetName,
            String healthStatus
    ) {
        public IntakeHorse(UUID horseId, String code, String name, String paymentStatus, String intakeStatus) {
            this(horseId, code, name, paymentStatus, intakeStatus, null, null, null);
        }
    }
}
