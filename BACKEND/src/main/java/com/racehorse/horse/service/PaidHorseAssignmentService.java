package com.racehorse.horse.service;

import com.racehorse.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class PaidHorseAssignmentService {
    private final JdbcTemplate jdbc;

    public PaidHorseAssignmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PaidHorse> pendingTrainer() {
        return jdbc.query("select h.id,h.code,h.name,h.payment_status from horses h where h.archived_at is null "
                        + "and (h.service_override=1 or (h.payment_status='PAID' and (h.paid_until is null or h.paid_until>=?))) "
                        + "and h.intake_status='STABLED' and h.health_status='FIT' "
                        + "and h.diagnosing_vet_user_id is not null and h.current_trainer_id is null "
                        + "and exists(select 1 from module_records m where m.horse_id=h.id and m.module_code='STABLE_CARE' "
                        + "and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE') "
                        + "and not exists(select 1 from training_locks tl where tl.horse_id=h.id and tl.released_at is null) "
                        + "order by updated_at,id",
                (rs, row) -> new PaidHorse(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("payment_status")),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Transactional
    public PaidHorse assignTrainer(UUID horseId, UUID trainerId) {
        int changed = jdbc.update("update horses set current_trainer_id=?,updated_at=SYSDATETIMEOFFSET(),"
                        + "version=version+1 where id=? and archived_at is null "
                        + "and (service_override=1 or (payment_status='PAID' and (paid_until is null or paid_until>=?))) "
                        + "and intake_status='STABLED' and health_status='FIT' and diagnosing_vet_user_id is not null "
                        + "and current_trainer_id is null "
                        + "and exists(select 1 from module_records m where m.horse_id=horses.id and m.module_code='STABLE_CARE' "
                        + "and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE') "
                        + "and not exists(select 1 from training_locks tl where tl.horse_id=horses.id and tl.released_at is null) "
                        + "and exists(select 1 from user_roles ur "
                        + "join users u on u.id=ur.user_id where ur.user_id=? "
                        + "and ur.role='HEAD_TRAINER' and u.enabled=1)",
                trainerId, horseId, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), trainerId);
        if (changed == 0) throw new BusinessException(
                "Ngựa chưa đủ điều kiện tiếp nhận hoặc đã có huấn luyện viên phụ trách");
        return jdbc.queryForObject("select id,code,name,payment_status from horses where id=?",
                (rs, row) -> new PaidHorse(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("payment_status")), horseId);
    }

    public record PaidHorse(UUID horseId, String code, String name, String paymentStatus) { }
}
