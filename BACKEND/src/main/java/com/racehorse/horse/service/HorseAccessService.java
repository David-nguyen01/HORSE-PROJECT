package com.racehorse.horse.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class HorseAccessService {

    private final HorseRepository horseRepository;
    private final HorseOwnershipRepository ownershipRepository;
    private final JdbcTemplate jdbc;

    @Autowired
    public HorseAccessService(
            HorseRepository horseRepository,
            HorseOwnershipRepository ownershipRepository,
            JdbcTemplate jdbc) {
        this.horseRepository = horseRepository;
        this.ownershipRepository = ownershipRepository;
        this.jdbc = jdbc;
    }

    public HorseAccessService(HorseRepository horseRepository, HorseOwnershipRepository ownershipRepository) {
        this(horseRepository, ownershipRepository, null);
    }

    public Horse requireReadable(UUID horseId, UUID userId, boolean privileged) {
        Horse horse = requireExisting(horseId);
        if (!privileged && (horse.getArchivedAt() != null
                || !ownershipRepository.existsByHorseIdAndOwnerId(horseId, userId))) {
            throw new ResourceNotFoundException("Không tìm thấy ngựa được phép truy cập: " + horseId);
        }
        return horse;
    }

    public Horse requireActive(UUID horseId) {
        Horse horse = requireExisting(horseId);
        if (horse.getArchivedAt() != null) {
            throw new ResourceNotFoundException("Hồ sơ ngựa đã được lưu trữ: " + horseId);
        }
        return horse;
    }

    public Horse requireTrainingAccess(UUID horseId, UUID userId,
            boolean manager, boolean trainer, boolean owner) {
        Horse horse = requireActive(horseId);
        if (manager) return horse;
        if (trainer && horse.getCurrentTrainer() != null
                && horse.getCurrentTrainer().getId().equals(userId)) return horse;
        if (owner && ownershipRepository.existsByHorseIdAndOwnerId(horseId, userId)) return horse;
        throw new ResourceNotFoundException("Không tìm thấy ngựa được phép truy cập: " + horseId);
    }

    public Horse requireServiceEligible(UUID horseId) {
        Horse horse = requireActive(horseId);
        boolean isPaid = "PAID".equalsIgnoreCase(horse.getPaymentStatus())
                && (horse.getPaidUntil() == null || !horse.getPaidUntil().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
        boolean isOverridden = Boolean.TRUE.equals(horse.getServiceOverride());
        if (!isPaid && !isOverridden) {
            throw new BusinessException("Ngựa " + horse.getName() + " chưa thanh toán phí dịch vụ chăm sóc và chưa được Club Manager duyệt bảo lãnh dịch vụ");
        }
        return horse;
    }

    public Horse requireTrainingReady(UUID horseId) {
        Horse horse = requireServiceEligible(horseId);
        if (!"STABLED".equals(horse.getIntakeStatus()) || horse.getDiagnosingVet() == null
                || !"FIT".equals(horse.getHealthStatus())) {
            throw new BusinessException("Ngựa phải khám xong, đủ điều kiện FIT và đã được phân ô chuồng trước khi huấn luyện");
        }
        if (jdbc != null) {
            Integer stallCount = jdbc.queryForObject("select count(*) from module_records where module_code='STABLE_CARE' "
                    + "and record_type='STALL_ASSIGNMENT' and status='ACTIVE' and horse_id=?", Integer.class, horseId);
            Integer lockCount = jdbc.queryForObject("select count(*) from training_locks where horse_id=? and released_at is null",
                    Integer.class, horseId);
            if (stallCount == null || stallCount == 0 || lockCount != null && lockCount > 0) {
                throw new BusinessException("Ngựa chưa có ô chuồng hoạt động hoặc vẫn đang bị khóa huấn luyện");
            }
        }
        return horse;
    }

    public Horse requireOwned(UUID horseId, UUID ownerId) {
        Horse horse = requireExisting(horseId);
        if (!ownershipRepository.existsByHorseIdAndOwnerId(horseId, ownerId)) {
            throw new ResourceNotFoundException("Không tìm thấy ngựa được phép truy cập: " + horseId);
        }
        return horse;
    }

    public Horse requireExisting(UUID horseId) {
        return findExisting(horseId);
    }

    private Horse findExisting(UUID horseId) {
        return horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));
    }
}
