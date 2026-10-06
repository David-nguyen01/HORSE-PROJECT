package com.racehorse.horse.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaidHorseAssignmentService {
    private final JdbcTemplate jdbc;

    public PaidHorseAssignmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PaidHorse> pendingCaretaker() {
        return pending("caretaker_user_id");
    }

    @Transactional(readOnly = true)
    public List<PaidHorse> pendingDiagnosingVet() {
        return pending("diagnosing_vet_user_id");
    }

    @Transactional
    public PaidHorse assignCaretaker(UUID horseId, UUID staffId) {
        return assign(horseId, staffId, "caretaker_user_id");
    }

    @Transactional
    public PaidHorse assignDiagnosingVet(UUID horseId, UUID veterinarianId) {
        return assign(horseId, veterinarianId, "diagnosing_vet_user_id");
    }

    private List<PaidHorse> pending(String column) {
        return jdbc.query("select id,code,name,payment_status from horses where archived_at is null "
                        + "and payment_status='PAID' and " + column + " is null order by updated_at,id",
                (rs, row) -> new PaidHorse(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("payment_status")));
    }

    private PaidHorse assign(UUID horseId, UUID userId, String column) {
        int changed = jdbc.update("update horses set " + column + "=?, updated_at=SYSDATETIMEOFFSET(), "
                        + "version=version+1 where id=? and archived_at is null and payment_status='PAID' and "
                        + column + " is null", userId, horseId);
        if (changed == 0) {
            List<String> states = jdbc.query("select payment_status from horses where id=? and archived_at is null",
                    (rs, row) -> rs.getString(1), horseId);
            if (states.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy ngựa đang hoạt động");
            if (!"PAID".equalsIgnoreCase(states.getFirst()))
                throw new BusinessException("Chỉ có thể nhận ngựa đã thanh toán");
            throw new BusinessException("Ngựa này đã được người khác nhận chăm sóc hoặc chẩn đoán");
        }
        return jdbc.queryForObject("select id,code,name,payment_status from horses where id=?",
                (rs, row) -> new PaidHorse(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("payment_status")), horseId);
    }

    public record PaidHorse(UUID horseId, String code, String name, String paymentStatus) { }
}
